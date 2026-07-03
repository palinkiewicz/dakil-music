package pl.dakil.music.presentation.quickplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.usecase.PlayAtFrontUseCase
import pl.dakil.music.domain.usecase.ResolveAudioUriUseCase

data class QuickPlayerUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Plays a single externally opened audio file in isolation. Owns a private
 * [ExoPlayer] separate from the main [pl.dakil.music.data.playback.PlaybackService]
 * player, so the user's queue is untouched; audio-focus handling makes the two
 * players pause each other automatically. Playback deliberately ends with this
 * ViewModel (no foreground service) — the quick player is a transient viewer.
 */
class QuickPlayerViewModel(
    application: Application,
    private val resolveAudioUri: ResolveAudioUriUseCase,
    private val playAtFront: PlayAtFrontUseCase,
) : ViewModel() {

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .build()

    private val _uiState = MutableStateFlow(QuickPlayerUiState())
    val uiState: StateFlow<QuickPlayerUiState> = _uiState.asStateFlow()

    private var loadedUri: Uri? = null

    init {
        // Same polling approach the app uses elsewhere: cheap, and immune to the
        // callback-ordering quirks of listener-driven position tracking.
        viewModelScope.launch {
            while (isActive) {
                _uiState.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L),
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Idempotent: called for both onCreate and onNewIntent of the activity. */
    fun load(uri: Uri) {
        if (uri == loadedUri) return
        loadedUri = uri
        viewModelScope.launch {
            val song = resolveAudioUri(uri)
            _uiState.update { it.copy(song = song) }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            // Replaying a finished track should restart it, not no-op at the end.
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    /**
     * Hands the current track off to the main app: stops local playback and inserts the
     * track at the front of the main queue, resuming from the exact current position.
     */
    fun handOffToMainApp() {
        val song = _uiState.value.song ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        player.pause()
        playAtFront(song, positionMs)
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))

    fun seekBy(deltaMs: Long) =
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
    }
}
