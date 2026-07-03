package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.Song

/**
 * Thin command surface over the Media3 MediaController. The UI never touches the
 * player directly; it observes [playbackState] and issues intents through here.
 */
interface PlayerRepository {

    val playbackState: StateFlow<PlaybackState>

    /** Replaces the queue with [songs] and starts playback at [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int)

    /** Appends [songs] to the end of the current queue. */
    fun addToQueue(songs: List<Song>)

    /**
     * Appends [songs] to the queue; if nothing is currently playing, immediately
     * starts playing the first appended track. Never interrupts ongoing playback.
     */
    fun enqueueOrPlay(songs: List<Song>)

    /**
     * Inserts [song] at the front of the queue and plays it from [positionMs],
     * preserving the rest of the queue (used to hand a track off from the quick player).
     */
    fun playAtFront(song: Song, positionMs: Long)

    /** Jumps to [index] in the current queue and plays. */
    fun skipToQueueItem(index: Int)

    /** Moves the queue item at [from] to [to]. */
    fun moveQueueItem(from: Int, to: Int)

    /** Removes the queue item at [index]. */
    fun removeQueueItem(index: Int)

    /** Empties the queue and stops playback. */
    fun clearQueue()

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeatMode()

    /** Sets the playback speed multiplier for the current session (not persisted). */
    fun setPlaybackSpeed(speed: Float)

    /** Starts (or replaces) an in-memory sleep timer that pauses playback after [durationMs]. */
    fun startSleepTimer(durationMs: Long)

    /** Arms a sleep timer that pauses playback when the current track finishes. */
    fun startSleepTimerEndOfTrack()

    /** Arms a sleep timer that pauses playback when the queue finishes playing. */
    fun startSleepTimerEndOfQueue()

    /** Cancels any running sleep timer. */
    fun cancelSleepTimer()

    /** Releases the underlying MediaController. Call when the process-level scope ends. */
    fun release()
}
