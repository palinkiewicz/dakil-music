package pl.dakil.music.presentation.nowplaying

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.ui.designsystem.rememberHaptics
import kotlin.math.roundToInt

/** Height of the collapsed (mini) player. */
val MiniPlayerHeight: Dp = 64.dp

/** Room the mini player takes above the navigation bar: its height plus a small gap around it. */
val MiniPlayerDockHeight: Dp = MiniPlayerHeight + 12.dp

internal val MiniArtSize: Dp = 48.dp
private val MiniSideInset: Dp = 12.dp
private val MiniCorner: Dp = 20.dp

/** Fling speed (px/s) beyond which a release expands or collapses regardless of position. */
private const val FLING_VELOCITY = 1200f

/**
 * How far the sheet is open: 0 is the mini player, 1 the full player.
 *
 * Dragging writes [value] synchronously, straight from the gesture. Animations run under a
 * [MutatorMutex], so a new touch (user input priority) cancels a running settle, and a settle is
 * never cancelled by stray drag updates landing after the finger has lifted — which is what froze
 * the sheet mid-way when each drag delta was applied in its own coroutine.
 */
@Stable
class PlayerSheetState(initiallyExpanded: Boolean = false) {
    internal var value by mutableFloatStateOf(if (initiallyExpanded) 1f else 0f)
        private set

    /** Where the sheet is headed or resting. */
    internal var targetValue by mutableFloatStateOf(value)
        private set

    private val mutex = MutatorMutex()

    /** Where the sheet is headed (or resting); true once it's on its way to the full player. */
    val isExpanded: Boolean get() = targetValue == 1f

    /**
     * The live open fraction, for anything that follows the sheet. Clamped: the expressive spring
     * overshoots its targets, which geometry (corner radii, insets) must not follow.
     */
    val fraction: Float get() = value.coerceIn(0f, 1f)

    internal var spec: FiniteAnimationSpec<Float> = spring()

    /** Follows a drag or a predictive-back gesture; call only while no animation should run. */
    internal fun snapTo(newValue: Float) {
        value = newValue.coerceIn(0f, 1f)
    }

    /** Takes the sheet over for a gesture, cancelling any running animation. */
    internal suspend fun stopAnimation() = mutex.mutate(MutatePriority.UserInput) {}

    internal suspend fun animateTo(target: Float, initialVelocity: Float = 0f) = mutex.mutate {
        targetValue = target
        animate(value, target, initialVelocity, spec) { v, _ -> value = v }
    }

    suspend fun expand() = animateTo(1f)

    suspend fun collapse() = animateTo(0f)
}

/** Survives rotation and process recreation, so an open player stays open. */
@Composable
fun rememberPlayerSheetState(): PlayerSheetState = rememberSaveable(
    saver = Saver(save = { it.isExpanded }, restore = { PlayerSheetState(initiallyExpanded = it) }),
) { PlayerSheetState() }

/**
 * The player layer drawn above the app: a mini player docked over the navigation bar that opens
 * into the full player. One surface grows from the dock to the whole screen while the cover flies
 * from the mini player's thumbnail into its place in the full player. Drag it, fling it, or use
 * (predictive) back to close. Themed from the cover art via [ArtTheme].
 *
 * [dockBottom] is the height of whatever sits under the dock (the navigation bar, insets included).
 */
@Composable
fun PlayerHost(
    sheet: PlayerSheetState,
    viewModel: NowPlayingViewModel,
    dockBottom: Dp,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    sheet.spec = MaterialTheme.motionScheme.defaultSpatialSpec()

    // Nothing to play: fold the sheet away.
    LaunchedEffect(state.song == null) {
        if (state.song == null) {
            sheet.stopAnimation()
            sheet.snapTo(0f)
        }
    }

    val expandedTarget by remember { derivedStateOf { sheet.targetValue == 1f } }
    // Composed only while the player is open, so its callback is registered at that moment and
    // outranks every handler the screen underneath registered earlier (the dispatcher asks the
    // most recently added callback first). An always-present handler that is merely toggled on
    // keeps its early slot, and back would close the album page behind the player first.
    if (expandedTarget && state.song != null) {
        PredictiveBackHandler { events ->
            try {
                sheet.stopAnimation()
                events.collect { event -> sheet.snapTo(1f - event.progress * 0.12f) }
                scope.launch { sheet.collapse() }
            } catch (e: CancellationException) {
                scope.launch { sheet.expand() }
                throw e
            }
        }
    }

    AnimatedVisibility(
        visible = state.song != null,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } + fadeIn(),
        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 4 } + fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        val song = state.song ?: return@AnimatedVisibility
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullW = constraints.maxWidth
            val fullH = constraints.maxHeight
            val miniH = with(density) { MiniPlayerHeight.roundToPx() }
            val sideInset = with(density) { MiniSideInset.toPx() }
            val dockGap = with(density) { (MiniPlayerDockHeight - MiniPlayerHeight).toPx() / 2f }
            val collapsedTop = fullH - with(density) { dockBottom.toPx() } - dockGap - miniH
            val travel by rememberUpdatedState(collapsedTop.coerceAtLeast(1f))

            // Synchronous: the sheet follows the finger in the same frame the delta arrives.
            val dragState = rememberDraggableState { delta -> sheet.snapTo(sheet.value - delta / travel) }
            fun settle(velocity: Float) {
                val target = when {
                    velocity < -FLING_VELOCITY -> 1f
                    velocity > FLING_VELOCITY -> 0f
                    sheet.value > 0.5f -> 1f
                    else -> 0f
                }
                if (target != sheet.value) haptics.tick()
                // In the host's scope, not the dragged node's: the node the drag started on (the
                // mini player, say) can leave composition mid-animation and would cancel it.
                scope.launch { sheet.animateTo(target, initialVelocity = -velocity / travel) }
            }
            val dragModifier = Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { sheet.stopAnimation() },
                onDragStopped = { velocity -> settle(velocity) },
            )

            val showFull by remember { derivedStateOf { sheet.value > 0f } }
            val showMini by remember { derivedStateOf { sheet.value < 1f } }
            var sheetCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var artSlot by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val flying by remember {
                derivedStateOf { sheet.value in 0.001f..0.999f && artSlot != null && showFull }
            }

            ArtTheme(artModel = song.coverArtModel()) {
                val miniColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val fullColor = MaterialTheme.colorScheme.surface
                val miniCornerPx = with(density) { MiniCorner.toPx() }
                val fullCornerPx = with(density) { state.coverArtRoundnessDp.dp.toPx() }

                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val p = sheet.fraction
                            val side = lerp(sideInset, 0f, p)
                            val width = (fullW - 2 * side).roundToInt()
                            val height = lerp(miniH.toFloat(), fullH.toFloat(), p).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(side.roundToInt(), lerp(collapsedTop, 0f, p).roundToInt())
                            }
                        }
                        .graphicsLayer {
                            val p = sheet.fraction
                            shape = RoundedCornerShape(lerp(miniCornerPx, 0f, p))
                            clip = true
                            shadowElevation = lerp(6.dp.toPx(), 0f, p)
                        }
                        .drawBehind { drawRect(lerp(miniColor, fullColor, sheet.fraction)) }
                        .onGloballyPositioned { sheetCoords = it },
                ) {
                    if (showFull) {
                        Box(
                            Modifier
                                .requiredSize(with(density) { fullW.toDp() }, with(density) { fullH.toDp() })
                                .align(Alignment.TopCenter)
                                .graphicsLayer { alpha = ((sheet.fraction - 0.15f) / 0.6f).coerceIn(0f, 1f) },
                        ) {
                            FullPlayer(
                                viewModel = viewModel,
                                state = state,
                                artVisible = !flying,
                                onArtSlotPlaced = { artSlot = it },
                                dragModifier = dragModifier,
                                onCollapse = { scope.launch { sheet.collapse() } },
                                onOpenAlbum = { id ->
                                    scope.launch { sheet.collapse() }
                                    onOpenAlbum(id)
                                },
                                onOpenArtist = { name ->
                                    scope.launch { sheet.collapse() }
                                    onOpenArtist(name)
                                },
                            )
                        }
                    }
                    if (showMini) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(MiniPlayerHeight)
                                .graphicsLayer { alpha = (1f - sheet.fraction / 0.3f).coerceIn(0f, 1f) }
                                .then(dragModifier)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { scope.launch { sheet.expand() } },
                        ) {
                            MiniPlayer(
                                state = state,
                                artVisible = true,
                                onPlayPause = viewModel::onPlayPause,
                                onNext = viewModel::onNext,
                                onPrevious = viewModel::onPrevious,
                            )
                        }
                    }
                    if (flying) {
                        FlyingArt(
                            model = song.coverArtModel(),
                            progress = { sheet.fraction },
                            from = {
                                val inset = with(density) { (MiniPlayerHeight - MiniArtSize).toPx() / 2f }
                                val size = with(density) { MiniArtSize.toPx() }
                                Rect(with(density) { 8.dp.toPx() }, inset, with(density) { 8.dp.toPx() } + size, inset + size)
                            },
                            to = {
                                val sheetC = sheetCoords
                                val slot = artSlot
                                if (sheetC != null && slot != null && sheetC.isAttached && slot.isAttached) {
                                    sheetC.localBoundingBoxOf(slot, clipBounds = false)
                                } else {
                                    null
                                }
                            },
                            fromCorner = with(density) { 14.dp.toPx() },
                            toCorner = fullCornerPx,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The cover in flight between the mini player and the full player, interpolated every frame in the
 * layout and draw phases only (no recomposition while dragging).
 */
@Composable
private fun FlyingArt(
    model: Any?,
    progress: () -> Float,
    from: () -> Rect,
    to: () -> Rect?,
    fromCorner: Float,
    toCorner: Float,
) {
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = progress()
                val target = to() ?: from()
                val rect = lerp(from(), target, p)
                val placeable = measurable.measure(
                    Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                }
            }
            .graphicsLayer {
                val p = progress()
                shape = RoundedCornerShape(lerp(fromCorner, toCorner, p))
                clip = true
                alpha = (p / 0.1f).coerceIn(0f, 1f)
            },
    ) {
        AlbumArt(model = model, shape = RectangleShape, modifier = Modifier.fillMaxSize())
    }
}
