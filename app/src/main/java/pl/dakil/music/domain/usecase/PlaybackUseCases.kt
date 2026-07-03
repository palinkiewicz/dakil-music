package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.PlayerRepository

class ObservePlaybackUseCase(private val player: PlayerRepository) {
    operator fun invoke(): StateFlow<PlaybackState> = player.playbackState
}

/** Sets the queue and starts playback at the chosen track. */
class PlaySongsUseCase(private val player: PlayerRepository) {
    operator fun invoke(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        player.play(songs, startIndex.coerceIn(0, songs.lastIndex))
    }
}

/** Shuffles [songs] into a random order and plays the resulting queue from the top. */
class ShufflePlayUseCase(private val player: PlayerRepository) {
    operator fun invoke(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.play(songs.shuffled(), 0)
    }
}

/** Appends songs to the end of the current queue. */
class AddToQueueUseCase(private val player: PlayerRepository) {
    operator fun invoke(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.addToQueue(songs)
    }
}

/**
 * Appends [songs]; when nothing is playing, immediately starts the first one.
 * Used by the "Add to Playback Queue" system action.
 */
class EnqueueOrPlayUseCase(private val player: PlayerRepository) {
    operator fun invoke(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.enqueueOrPlay(songs)
    }
}

/**
 * Inserts [song] at the front of the queue and resumes it from [positionMs].
 * Used to hand a track off from the quick player into the main app.
 */
class PlayAtFrontUseCase(private val player: PlayerRepository) {
    operator fun invoke(song: Song, positionMs: Long) = player.playAtFront(song, positionMs)
}

/** Bundles the simple transport commands so the ViewModel depends on one object. */
class PlaybackControlUseCase(private val player: PlayerRepository) {
    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeatMode() = player.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    fun startSleepTimer(durationMs: Long) = player.startSleepTimer(durationMs)
    fun startSleepTimerEndOfTrack() = player.startSleepTimerEndOfTrack()
    fun startSleepTimerEndOfQueue() = player.startSleepTimerEndOfQueue()
    fun cancelSleepTimer() = player.cancelSleepTimer()
    fun skipToQueueItem(index: Int) = player.skipToQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = player.removeQueueItem(index)
    fun clearQueue() = player.clearQueue()
}
