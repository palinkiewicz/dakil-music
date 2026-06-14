package pl.dakil.music.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song

data class NowPlayingUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isCurrentFavorite: Boolean = false,
)

class NowPlayingViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<NowPlayingUiState> = combine(
        container.observePlayback(),
        container.observeFavorites(),
    ) { playback: PlaybackState, favorites ->
        NowPlayingUiState(
            song = playback.currentSong,
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            hasNext = playback.hasNext,
            hasPrevious = playback.hasPrevious,
            shuffleEnabled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            isCurrentFavorite = playback.currentSong?.id in favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    fun onPlayPause() = container.playbackControl.togglePlayPause()
    fun onNext() = container.playbackControl.next()
    fun onPrevious() = container.playbackControl.previous()
    fun onSeek(positionMs: Long) = container.playbackControl.seekTo(positionMs)
    fun onToggleShuffle() = container.playbackControl.toggleShuffle()
    fun onCycleRepeat() = container.playbackControl.cycleRepeatMode()

    fun onToggleFavorite() {
        val id = uiState.value.song?.id ?: return
        viewModelScope.launch { container.toggleFavorite(id) }
    }
}
