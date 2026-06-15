package pl.dakil.music.data.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * Durability comes from flushing on every track boundary and on STATE_ENDED — not
 * from process teardown, which is unreliable. (We deliberately do NOT flush when
 * the app is backgrounded: playback continues in the foreground service.)
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
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var active: ActiveSession? = null
    private var isPlaying = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            settleDelta()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                active?.let { it.timesPlayed++ }
            } else {
                flushActive()
                startSession()
            }
            if (isPlaying) active?.lastResumeElapsedMs = SystemClock.elapsedRealtime()
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) {
                active?.lastResumeElapsedMs = SystemClock.elapsedRealtime()
            } else {
                settleDelta()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                settleDelta()
                flushActive()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            settleDelta()
            flushActive()
        }
    }

    init {
        source.addRawListener(listener)
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

    private fun flushActive() {
        val s = active ?: return
        active = null
        scope.launch { persist(s) }
    }

    private suspend fun persist(s: ActiveSession) {
        if (s.accumulatedMs <= 0L) return
        val settings = settingsRepository.settings.first()
        if (!settings.statisticsEnabled) return
        val seconds = s.accumulatedMs / 1000L
        if (seconds < settings.minPlaySeconds) return
        val song = s.song
        historyRepository.record(
            ListeningRecord(
                songId = song.id,
                startTimestamp = s.startWallMs,
                secondsPlayed = seconds.toInt(),
                timesPlayed = s.timesPlayed,
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
        settleDelta()
        val s = active
        active = null
        if (s != null) runBlocking { persist(s) }
        scope.cancel()
    }
}
