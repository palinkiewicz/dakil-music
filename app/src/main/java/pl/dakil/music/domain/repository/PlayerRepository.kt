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

    /** Jumps to [index] in the current queue and plays. */
    fun skipToQueueItem(index: Int)

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeatMode()

    /** Releases the underlying MediaController. Call when the process-level scope ends. */
    fun release()
}
