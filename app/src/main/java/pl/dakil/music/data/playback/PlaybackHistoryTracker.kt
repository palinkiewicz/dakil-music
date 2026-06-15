package pl.dakil.music.data.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.util.ContentKey

/**
 * Records listening sessions by observing raw Media3 events from [source].
 *
 * A session is the contiguous span one song is the current track. Looping
 * (repeat-one) keeps the session and bumps [ActiveSession.timesPlayed]; any other
 * transition flushes and opens a new one. Listened time is measured from the
 * monotonic clock, gated by play/pause, so paused/buffering time is never counted.
 *
 * The active session is also **checkpointed** to disk on an interval (see
 * `historyUpdateSeconds`) so an in-progress listen survives the process being
 * killed. The first checkpoint inserts a row; later ones update it in place.
 *
 * In-memory session state is touched from both the player's main thread (listener
 * callbacks) and the background checkpoint coroutine, so all mutations are guarded
 * by [lock]; the suspending DB writes happen outside the lock on immutable copies.
 */
class PlaybackHistoryTracker(
    private val source: PlaybackTrackingSource,
    private val historyRepository: ListeningHistoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    private class ActiveSession(
        val song: Song,
        val startWallMs: Long,
        var accumulatedMs: Long = 0L,
        var lastResumeElapsedMs: Long = -1L,
        var timesPlayed: Int = 1,
        /** Row id once persisted (by a checkpoint), else 0. */
        var recordId: Long = 0L,
    )

    /** Immutable copy handed to the DB layer outside the lock. */
    private class Snapshot(
        val session: ActiveSession,
        val recordId: Long,
        val song: Song,
        val startWallMs: Long,
        val accumulatedMs: Long,
        val timesPlayed: Int,
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()
    private var active: ActiveSession? = null
    private var isPlaying = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val toFlush: ActiveSession? = synchronized(lock) {
                settleDelta()
                val previous: ActiveSession?
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    active?.timesPlayed = (active?.timesPlayed ?: 1) + 1
                    previous = null
                } else {
                    previous = active
                    startSession()
                }
                // Re-arm so listened time keeps accruing (settleDelta cleared it).
                if (isPlaying) active?.lastResumeElapsedMs = SystemClock.elapsedRealtime()
                previous
            }
            if (toFlush != null) flush(toFlush)
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            synchronized(lock) {
                isPlaying = playing
                if (playing) active?.lastResumeElapsedMs = SystemClock.elapsedRealtime() else settleDelta()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) flushCurrent()
        }

        override fun onPlayerError(error: PlaybackException) {
            flushCurrent()
        }
    }

    init {
        source.addRawListener(listener)
        scope.launch { checkpointLoop() }
    }

    /** Banks the time elapsed since the last resume into the active session. */
    private fun settleDelta() {
        val s = active ?: return
        val resume = s.lastResumeElapsedMs
        if (resume >= 0) {
            s.accumulatedMs += (SystemClock.elapsedRealtime() - resume).coerceAtLeast(0L)
            s.lastResumeElapsedMs = -1L
        }
    }

    private fun startSession() {
        val song = source.currentSongSnapshot()
        active = song?.let { ActiveSession(song = it, startWallMs = System.currentTimeMillis()) }
    }

    /** Settles and detaches the current session, then finalizes it on disk. */
    private fun flushCurrent() {
        val toFlush = synchronized(lock) {
            settleDelta()
            val current = active
            active = null
            current
        }
        if (toFlush != null) flush(toFlush)
    }

    private fun flush(session: ActiveSession) {
        scope.launch {
            persist(session.recordId, session.song, session.startWallMs, session.accumulatedMs, session.timesPlayed)
        }
    }

    /** Periodically writes the in-progress session so a kill doesn't lose it. */
    private suspend fun checkpointLoop() {
        while (scope.isActive) {
            val interval = settingsRepository.settings.first().historyUpdateSeconds
            if (interval <= 0) {
                delay(1_000L) // checkpointing off; re-check the setting shortly.
                continue
            }
            delay(interval * 1_000L)
            checkpoint()
        }
    }

    private suspend fun checkpoint() {
        val snapshot: Snapshot = synchronized(lock) {
            settleDelta()
            val s = active ?: return
            // Keep counting: re-arm so the next tick measures from now.
            if (isPlaying) s.lastResumeElapsedMs = SystemClock.elapsedRealtime()
            Snapshot(s, s.recordId, s.song, s.startWallMs, s.accumulatedMs, s.timesPlayed)
        }
        val id = persist(snapshot.recordId, snapshot.song, snapshot.startWallMs, snapshot.accumulatedMs, snapshot.timesPlayed)
            ?: return
        synchronized(lock) {
            if (active === snapshot.session) snapshot.session.recordId = id
        }
    }

    /** Inserts or updates the record; returns the row id, or null if not persisted. */
    private suspend fun persist(
        recordId: Long,
        song: Song,
        startWallMs: Long,
        accumulatedMs: Long,
        timesPlayed: Int,
    ): Long? {
        if (accumulatedMs <= 0L) return null
        val settings = settingsRepository.settings.first()
        if (!settings.statisticsEnabled) return null
        val seconds = accumulatedMs / 1000L
        if (seconds < settings.minPlaySeconds) return null
        return historyRepository.upsert(
            ListeningRecord(
                id = recordId,
                songId = song.id,
                startTimestamp = startWallMs,
                secondsPlayed = seconds.toInt(),
                timesPlayed = timesPlayed,
                title = song.title,
                artists = song.artists,
                album = song.album,
                albumId = song.albumId,
                albumArtUri = song.albumArtUri,
                durationMs = song.durationMs,
                contentKey = ContentKey.of(song),
            ),
        )
    }

    /** Best-effort final flush; called before the player is released. */
    fun release() {
        val toFlush = synchronized(lock) {
            settleDelta()
            val current = active
            active = null
            current
        }
        if (toFlush != null) {
            runBlocking {
                persist(toFlush.recordId, toFlush.song, toFlush.startWallMs, toFlush.accumulatedMs, toFlush.timesPlayed)
            }
        }
        scope.cancel()
    }
}
