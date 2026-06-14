package pl.dakil.music.data.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.dakil.music.domain.model.PlaybackState
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.PlayerRepository

/**
 * [PlayerRepository] backed by a Media3 [MediaController] bound to [PlaybackService].
 *
 * The controller can only be touched on the main thread, so all access happens on
 * [mainScope]. A lightweight polling loop advances the position while playing —
 * the player itself doesn't emit per-frame position callbacks.
 */
class MediaControllerPlayerRepository(
    context: Context,
) : PlayerRepository {

    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null

    /** Maps mediaId -> Song so the current item resolves back to a rich domain model. */
    private val queueById = HashMap<String, Song>()

    /** Action requested before the controller finished connecting. */
    private var pendingAction: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Coarse but correct: re-derive the whole snapshot on any relevant change.
            syncState()
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = future.get().also { it.addListener(listener) }
                syncState()
                pendingAction?.invoke()
                pendingAction = null
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    override fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: run {
            pendingAction = { play(songs, startIndex) }
            return
        }
        queueById.clear()
        songs.forEach { queueById[it.id.toString()] = it }
        c.setMediaItems(songs.map(MediaItemMapper::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    override fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    override fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun release() {
        positionJob?.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
    }

    private fun syncState() {
        val c = controller ?: return
        val song = c.currentMediaItem?.mediaId?.let(queueById::get)
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.coerceAtLeast(0L),
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode.toRepeatMode(),
            )
        }
        startOrStopPositionUpdates(c.isPlaying)
    }

    private fun startOrStopPositionUpdates(isPlaying: Boolean) {
        positionJob?.cancel()
        if (!isPlaying) return
        positionJob = mainScope.launch {
            while (isActive) {
                controller?.let { c ->
                    _playbackState.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
