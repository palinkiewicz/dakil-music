package pl.dakil.music.data.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import pl.dakil.music.domain.model.SleepTimerMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.PlayerRepository

/**
 * [PlayerRepository] backed by a Media3 [MediaController] bound to [PlaybackService].
 *
 * The controller can only be touched on the main thread, so all access happens on
 * [mainScope]. A lightweight polling loop advances the position while playing —
 * the player itself doesn't emit per-frame position callbacks.
 */
/**
 * Internal seam letting [PlaybackHistoryTracker] read the current playback state on
 * the main thread (the controller is main-thread only). Polling these is more
 * robust than reacting to the controller's transition/discontinuity callbacks,
 * which arrive inconsistently through the session proxy.
 */
interface PlaybackTrackingSource {
    fun currentSongSnapshot(): Song?
    fun currentPositionMs(): Long
    fun isPlaying(): Boolean
}

class MediaControllerPlayerRepository(
    context: Context,
) : PlayerRepository, PlaybackTrackingSource {

    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null

    /** Armed end-of-track / end-of-queue sleep mode, or null when none is set. */
    private var sleepMode: SleepTimerMode? = null

    /** Queue index of the item current when [onMediaItemTransition] last fired (wrap detection). */
    private var lastTransitionIndex = -1

    /** Maps mediaId -> Song so the current item resolves back to a rich domain model. */
    private val queueById = HashMap<String, Song>()

    /** Action requested before the controller finished connecting. */
    private var pendingAction: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Coarse but correct: re-derive the whole snapshot on any relevant change.
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onSleepBoundary(reason)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Reaching the end with repeat off naturally stops; disarm an end-of-* timer.
            if (playbackState == Player.STATE_ENDED && sleepMode != null) disarmSleepMode()
        }
    }

    init {
        connect()
    }

    @OptIn(markerClass = [UnstableApi::class])
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

    override fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller ?: run {
            pendingAction = { addToQueue(songs) }
            return
        }
        songs.forEach { queueById[it.id.toString()] = it }
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(songs.map(MediaItemMapper::toMediaItem))
        if (wasEmpty) c.prepare()
        syncState()
    }

    override fun enqueueOrPlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller ?: run {
            // Deferred until connected, so isPlaying reflects any live background session.
            pendingAction = { enqueueOrPlay(songs) }
            return
        }
        songs.forEach { queueById[it.id.toString()] = it }
        val startIndex = c.mediaItemCount
        c.addMediaItems(songs.map(MediaItemMapper::toMediaItem))
        // Idle → start the first appended track; already playing → leave it untouched.
        if (!c.isPlaying) {
            c.seekToDefaultPosition(startIndex)
            if (c.playbackState == Player.STATE_IDLE || c.playbackState == Player.STATE_ENDED) c.prepare()
            c.play()
        }
        syncState()
    }

    override fun playAtFront(song: Song, positionMs: Long) {
        val c = controller ?: run {
            pendingAction = { playAtFront(song, positionMs) }
            return
        }
        queueById[song.id.toString()] = song
        c.addMediaItem(0, MediaItemMapper.toMediaItem(song))
        c.seekTo(0, positionMs.coerceAtLeast(0L))
        if (c.playbackState == Player.STATE_IDLE || c.playbackState == Player.STATE_ENDED) c.prepare()
        c.play()
        syncState()
    }

    override fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) {
            c.moveMediaItem(from, to)
        }
    }

    override fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    override fun clearQueue() {
        queueById.clear()
        controller?.clearMediaItems()
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

    override fun setPlaybackSpeed(speed: Float) {
        val c = controller ?: run {
            pendingAction = { setPlaybackSpeed(speed) }
            return
        }
        c.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
        syncState()
    }

    override fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        sleepMode = null
        if (durationMs <= 0L) {
            _playbackState.update { it.copy(sleepTimerRemainingMs = null, sleepTimerMode = null) }
            return
        }
        val deadline = SystemClock.elapsedRealtime() + durationMs
        sleepTimerJob = mainScope.launch {
            while (isActive) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    controller?.pause()
                    _playbackState.update { it.copy(sleepTimerRemainingMs = null) }
                    return@launch
                }
                _playbackState.update { it.copy(sleepTimerRemainingMs = remaining, sleepTimerMode = null) }
                delay(SLEEP_TICK_MS)
            }
        }
    }

    override fun startSleepTimerEndOfTrack() = armSleepMode(SleepTimerMode.END_OF_TRACK)

    override fun startSleepTimerEndOfQueue() = armSleepMode(SleepTimerMode.END_OF_QUEUE)

    private fun armSleepMode(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepMode = mode
        lastTransitionIndex = controller?.currentMediaItemIndex ?: -1
        _playbackState.update { it.copy(sleepTimerRemainingMs = null, sleepTimerMode = mode) }
    }

    /** A media-item transition fired; pause if it crosses the armed sleep boundary. */
    private fun onSleepBoundary(reason: Int) {
        val mode = sleepMode ?: return
        val c = controller ?: return
        val newIndex = c.currentMediaItemIndex
        // Only auto-advance / repeat transitions count as a boundary; seeks don't.
        val autoAdvanced = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (autoAdvanced) {
            val crossed = when (mode) {
                SleepTimerMode.END_OF_TRACK -> true
                // Queue end is reached when auto-advance wraps back to an earlier item.
                SleepTimerMode.END_OF_QUEUE -> newIndex <= lastTransitionIndex
            }
            if (crossed) {
                c.pause()
                c.seekTo(lastTransitionIndex.coerceAtLeast(0), 0L)
                disarmSleepMode()
            }
        }
        lastTransitionIndex = newIndex
    }

    private fun disarmSleepMode() {
        sleepMode = null
        _playbackState.update { it.copy(sleepTimerMode = null) }
    }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepMode = null
        _playbackState.update { it.copy(sleepTimerRemainingMs = null, sleepTimerMode = null) }
    }

    override fun currentSongSnapshot(): Song? =
        controller?.currentMediaItem?.let(::resolveSong)

    /** Resolves a [MediaItem] to a rich [Song] (enqueued by the app) or a partial one. */
    private fun resolveSong(item: MediaItem): Song? =
        queueById[item.mediaId] ?: MediaItemMapper.toSong(item)

    override fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    override fun isPlaying(): Boolean = controller?.isPlaying == true

    override fun release() {
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
    }

    private fun syncState() {
        val c = controller ?: return
        val song = c.currentMediaItem?.let(::resolveSong)
        val queue = ArrayList<Song>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            resolveSong(c.getMediaItemAt(i))?.let(queue::add)
        }
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
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                playbackSpeed = c.playbackParameters.speed,
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
        const val SLEEP_TICK_MS = 1_000L
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3.0f
    }
}
