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

/** Bundles the simple transport commands so the ViewModel depends on one object. */
class PlaybackControlUseCase(private val player: PlayerRepository) {
    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeatMode() = player.cycleRepeatMode()
    fun skipToQueueItem(index: Int) = player.skipToQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = player.removeQueueItem(index)
    fun clearQueue() = player.clearQueue()
}
