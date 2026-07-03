package pl.dakil.music.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.data.playback.LyricsStatus
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.AudioEffectsCapabilities
import pl.dakil.music.domain.model.AudioEffectsSettings
import pl.dakil.music.domain.model.LyricLine
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.SleepTimerMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.util.ContentKey
import pl.dakil.music.domain.util.LrcParser

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
    val queueRemoveMode: QueueRemoveMode = QueueRemoveMode.SWIPE,
    val coverArtRoundnessDp: Int = 32,
    val playbackSpeed: Float = 1f,
    val sleepTimerRemainingMs: Long? = null,
    val sleepTimerMode: SleepTimerMode? = null,
)

/** State for the equalizer bottom sheet: effect capabilities + the persisted settings. */
data class EqualizerUiState(
    val capabilities: AudioEffectsCapabilities = AudioEffectsCapabilities(),
    val settings: AudioEffectsSettings = AudioEffectsSettings(),
) {
    /** Band levels (millibels) to display, resolved from the active preset or manual values. */
    val effectiveBandLevels: List<Int>
        get() = when {
            settings.preset in capabilities.presetBandLevelsMb.indices ->
                capabilities.presetBandLevelsMb[settings.preset]
            settings.bandLevelsMb.size == capabilities.numberOfBands -> settings.bandLevelsMb
            else -> List(capabilities.numberOfBands) { 0 }
        }
}

/** Compact lyrics state for the Now Playing card. */
data class LyricsCardState(
    val visible: Boolean = false,
    val status: LyricsStatus = LyricsStatus.SEARCHING,
    val synced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val activeLineIndex: Int = -1,
)

class NowPlayingViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<NowPlayingUiState> = combine(
        container.observePlayback(),
        container.observeFavorites(),
        container.observeSettings(),
        container.musicRepository.annotatedSongs,
    ) { playback: PlaybackState, favorites, settings, annotated ->
        // Carry each song's individual-cover-art flag over from the library so the
        // big art and queue rows honor the per-album cover-art setting/rule.
        val individualById = annotated.asSequence()
            .filter { it.individualCoverArt }
            .map { it.id }
            .toHashSet()
        fun annotate(s: Song?) = s?.copy(individualCoverArt = s.id in individualById)
        NowPlayingUiState(
            song = annotate(playback.currentSong),
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            hasNext = playback.hasNext,
            hasPrevious = playback.hasPrevious,
            shuffleEnabled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            isCurrentFavorite = playback.currentSong?.id in favorites,
            queue = playback.queue.map { it.copy(individualCoverArt = it.id in individualById) },
            currentIndex = playback.currentIndex,
            queueRemoveMode = settings.queueRemoveMode,
            coverArtRoundnessDp = settings.nowPlayingCornerRoundnessDp,
            playbackSpeed = playback.playbackSpeed,
            sleepTimerRemainingMs = playback.sleepTimerRemainingMs,
            sleepTimerMode = playback.sleepTimerMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentOffsetMs = container.lyricsController.state
        .map { it.song }
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .flatMapLatest { song ->
            if (song == null) flowOf(0L)
            else container.lyricsAlignmentRepository.offsetMs(ContentKey.of(song))
        }

    /** Lyrics shown in the Now Playing card; the active line tracks the playhead + offset. */
    val lyrics: StateFlow<LyricsCardState> = combine(
        container.lyricsController.state,
        container.observePlayback(),
        currentOffsetMs,
    ) { lyricsState, playback, offset ->
        val lyrics = lyricsState.lyrics
        val synced = lyrics?.synced == true
        LyricsCardState(
            visible = lyricsState.visible,
            status = lyricsState.status,
            synced = synced,
            lines = lyrics?.lines.orEmpty(),
            activeLineIndex = if (synced) {
                LrcParser.activeIndex(lyrics!!.lines, playback.positionMs - offset)
            } else {
                -1
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsCardState())

    val userPlaylists: StateFlow<List<UserPlaylist>> = container.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Device effect capabilities are constant; read once and pair with the live settings.
    private val audioEffectsCapabilities = container.audioEffectsCapabilitiesProvider.get()

    val equalizer: StateFlow<EqualizerUiState> = container.observeAudioEffects()
        .map { EqualizerUiState(audioEffectsCapabilities, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            EqualizerUiState(audioEffectsCapabilities),
        )

    /** True while the "add current song to playlist" dialog is open. */
    private val _showAddToPlaylist = MutableStateFlow(false)
    val showAddToPlaylist: StateFlow<Boolean> = _showAddToPlaylist.asStateFlow()

    fun onPlayPause() = container.playbackControl.togglePlayPause()
    fun onNext() = container.playbackControl.next()
    fun onPrevious() = container.playbackControl.previous()
    fun onSeek(positionMs: Long) = container.playbackControl.seekTo(positionMs)
    fun onToggleShuffle() = container.playbackControl.toggleShuffle()
    fun onCycleRepeat() = container.playbackControl.cycleRepeatMode()
    fun onSetSpeed(speed: Float) = container.playbackControl.setPlaybackSpeed(speed)
    fun onStartSleepTimer(durationMs: Long) = container.playbackControl.startSleepTimer(durationMs)
    fun onStartSleepTimerEndOfTrack() = container.playbackControl.startSleepTimerEndOfTrack()
    fun onStartSleepTimerEndOfQueue() = container.playbackControl.startSleepTimerEndOfQueue()
    fun onCancelSleepTimer() = container.playbackControl.cancelSleepTimer()
    fun onQueueItemClick(index: Int) = container.playbackControl.skipToQueueItem(index)
    fun onMoveQueueItem(from: Int, to: Int) = container.playbackControl.moveQueueItem(from, to)
    fun onRemoveQueueItem(index: Int) = container.playbackControl.removeQueueItem(index)
    fun onClearQueue() = container.playbackControl.clearQueue()

    fun onSetEqMasterEnabled(enabled: Boolean) = viewModelScope.launch {
        container.updateAudioEffects.setMasterEnabled(enabled)
    }

    fun onSelectEqPreset(preset: Int) = viewModelScope.launch {
        container.updateAudioEffects.setPreset(preset)
    }

    fun onSetEqBandLevel(index: Int, levelMb: Int) = viewModelScope.launch {
        val levels = equalizer.value.effectiveBandLevels.toMutableList()
        if (index !in levels.indices) return@launch
        levels[index] = levelMb
        container.updateAudioEffects.setBandLevels(levels)
    }

    fun onSetBassBoost(strength: Int) = viewModelScope.launch {
        container.updateAudioEffects.setBassBoostStrength(strength)
    }

    fun onResetEqualizer() = viewModelScope.launch {
        container.updateAudioEffects.resetToFlat()
    }

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
