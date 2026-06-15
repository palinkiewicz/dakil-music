package pl.dakil.music.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.UserPlaylist

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
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
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
            queue = playback.queue,
            currentIndex = playback.currentIndex,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    val userPlaylists: StateFlow<List<UserPlaylist>> = container.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while the "add current song to playlist" dialog is open. */
    private val _showAddToPlaylist = MutableStateFlow(false)
    val showAddToPlaylist: StateFlow<Boolean> = _showAddToPlaylist.asStateFlow()

    fun onPlayPause() = container.playbackControl.togglePlayPause()
    fun onNext() = container.playbackControl.next()
    fun onPrevious() = container.playbackControl.previous()
    fun onSeek(positionMs: Long) = container.playbackControl.seekTo(positionMs)
    fun onToggleShuffle() = container.playbackControl.toggleShuffle()
    fun onCycleRepeat() = container.playbackControl.cycleRepeatMode()
    fun onQueueItemClick(index: Int) = container.playbackControl.skipToQueueItem(index)
    fun onClearQueue() = container.playbackControl.clearQueue()

    fun onToggleFavorite() {
        val id = uiState.value.song?.id ?: return
        viewModelScope.launch { container.toggleFavorite(id) }
    }

    fun openAddToPlaylist() {
        if (uiState.value.song != null) _showAddToPlaylist.value = true
    }

    fun dismissAddToPlaylist() {
        _showAddToPlaylist.value = false
    }

    fun addCurrentToPlaylist(playlistId: String) {
        val songId = uiState.value.song?.id ?: return
        viewModelScope.launch {
            container.addSongsToPlaylist(playlistId, listOf(songId))
            _showAddToPlaylist.value = false
        }
    }

    fun createPlaylistAndAddCurrent(name: String) {
        val songId = uiState.value.song?.id ?: return
        viewModelScope.launch {
            val id = container.createPlaylist(name)
            container.addSongsToPlaylist(id, listOf(songId))
            _showAddToPlaylist.value = false
        }
    }
}
