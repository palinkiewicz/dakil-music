package pl.dakil.music.data.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.util.ContentKey

/**
 * Records listening sessions by polling the player once a second on the main
 * thread. Polling is deliberately used instead of reacting to Media3's
 * transition/discontinuity callbacks, which arrive inconsistently through the
 * session proxy and previously caused looped songs to be miscounted.
 *
 * A session is the contiguous span one song is the current track. While the song
 * keeps playing, listened time accumulates (paused time excluded). When the song
 * loops — the play position jumps from near the end back to the start —
 * [ActiveSession.timesPlayed] is bumped. A change to a different song flushes the
 * old session and opens a new one.
 *
 * The active session is also checkpointed to disk on the user-configured interval
 * so an in-progress listen survives the process being killed; the first checkpoint
 * inserts a row and later ones update it in place.
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
        var lastPersistElapsedMs: Long = 0L,
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

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val lock = Any()
    private var active: ActiveSession? = null
    private var lastPositionMs = 0L

    @Volatile
    private var settings = AppSettings()

    init {
        scope.launch { settingsRepository.settings.collect { settings = it } }
        scope.launch {
            while (scope.isActive) {
                tick()
                delay(POLL_MS)
            }
        }
    }

    /** One poll: reconcile the in-memory session with the live player, then persist. */
    private suspend fun tick() {
        val song = source.currentSongSnapshot()
        val position = source.currentPositionMs()
        val playing = source.isPlaying()
        val now = SystemClock.elapsedRealtime()

        var toFlush: Snapshot? = null
        var checkpoint: Snapshot? = null

        synchronized(lock) {
            val current = active
            if (song == null) {
                // Nothing loaded — finalize any session in progress.
                if (current != null) {
                    settleDelta(now)
                    toFlush = current.snapshot()
                    active = null
                }
            } else if (current == null || current.song.id != song.id) {
                // New (or first) song — finalize the previous one and start fresh.
                if (current != null) {
                    settleDelta(now)
                    toFlush = current.snapshot()
                }
                active = ActiveSession(song = song, startWallMs = System.currentTimeMillis()).also {
                    if (playing) it.lastResumeElapsedMs = now
                }
            } else {
                // Same song still current.
                if (lastPositionMs - position > LOOP_BACK_JUMP_MS && position < LOOP_START_MS) {
                    current.timesPlayed++ // looped back to the start = another play
                }
                if (playing) {
                    if (current.lastResumeElapsedMs < 0) current.lastResumeElapsedMs = now
                } else {
                    settleDelta(now)
                }
            }
            lastPositionMs = position

            val a = active
            val interval = settings.historyUpdateSeconds
            if (a != null && interval > 0 &&
                (a.lastPersistElapsedMs == 0L || now - a.lastPersistElapsedMs >= interval * 1000L)
            ) {
                settleDelta(now)
                if (playing) a.lastResumeElapsedMs = now
                a.lastPersistElapsedMs = now
                checkpoint = a.snapshot()
            }
        }

        toFlush?.let { persist(it) }
        checkpoint?.let { snap ->
            val id = persist(snap)
            if (id != null) synchronized(lock) { if (active === snap.session) snap.session.recordId = id }
        }
    }

    /** Banks time elapsed since the last resume into the active session. */
    private fun settleDelta(now: Long) {
        val s = active ?: return
        val resume = s.lastResumeElapsedMs
        if (resume >= 0) {
            s.accumulatedMs += (now - resume).coerceAtLeast(0L)
            s.lastResumeElapsedMs = -1L
        }
    }

    private fun ActiveSession.snapshot(): Snapshot =
        Snapshot(this, recordId, song, startWallMs, accumulatedMs, timesPlayed)

    /** Inserts or updates the record; returns the row id, or null if not persisted. */
    private suspend fun persist(s: Snapshot): Long? {
        if (s.accumulatedMs <= 0L) return null
        if (!settings.statisticsEnabled) return null
        val seconds = s.accumulatedMs / 1000L
        if (seconds < settings.minPlaySeconds) return null
        return historyRepository.upsert(
            ListeningRecord(
                id = s.recordId,
                songId = s.song.id,
                startTimestamp = s.startWallMs,
                secondsPlayed = seconds.toInt(),
                timesPlayed = s.timesPlayed,
                title = s.song.title,
                artists = s.song.artists,
                album = s.song.album,
                albumId = s.song.albumId,
                albumArtUri = s.song.albumArtUri,
                durationMs = s.song.durationMs,
                contentKey = ContentKey.of(s.song),
            ),
        )
    }

    /** Best-effort final flush; called before the player is released. */
    fun release() {
        val toFlush = synchronized(lock) {
            settleDelta(SystemClock.elapsedRealtime())
            val current = active
            active = null
            current?.snapshot()
        }
        if (toFlush != null) runBlocking { persist(toFlush) }
        scope.cancel()
    }

    private companion object {
        const val POLL_MS = 1_000L

        /** A backward position jump larger than this, landing near the start, is a loop. */
        const val LOOP_BACK_JUMP_MS = 1_500L
        const val LOOP_START_MS = 2_500L
    }
}
