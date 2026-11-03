package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fling speed (px/s) beyond which a release opens or closes regardless of position. */
private const val FLING_VELOCITY = 1200f

/**
 * A 0…1 progress that follows the finger and springs to an end when let go: the player sheet, the
 * cover's flip to lyrics, the queue panel.
 *
 * Dragging writes [value] synchronously, straight from the gesture. Animations run under a
 * [MutatorMutex], so a new touch (user input priority) cancels a running settle, and a settle is
 * never cancelled by stray drag updates landing after the finger has lifted — which is what froze
 * the sheet mid-way when each drag delta was applied in its own coroutine.
 */
@Stable
open class GestureProgress(initial: Float) {
    internal var value by mutableFloatStateOf(initial)
        private set

    /** Where the progress is headed or resting. */
    internal var targetValue by mutableFloatStateOf(initial)
        private set

    private val mutex = MutatorMutex()

    /**
     * The live fraction, for anything that follows it. Clamped: the expressive spring overshoots
     * its targets, which geometry (corner radii, insets) must not follow.
     */
    val fraction: Float get() = value.coerceIn(0f, 1f)

    internal var spec: FiniteAnimationSpec<Float> = spring()

    /** Follows a drag or a predictive-back gesture; call only while no animation should run. */
    internal fun snapTo(newValue: Float) {
        value = newValue.coerceIn(0f, 1f)
    }

    /** Takes the progress over for a gesture, cancelling any running animation. */
    internal suspend fun stopAnimation() = mutex.mutate(MutatePriority.UserInput) {}

    internal suspend fun animateTo(target: Float, initialVelocity: Float = 0f) = mutex.mutate {
        targetValue = target
        animate(value, target, initialVelocity, spec) { v, _ -> value = v }
    }
}

/** Something a vertical drag can drive. Deltas and velocities are raw pointer y: up is negative. */
interface DragTarget {
    suspend fun onStart()
    fun onDelta(delta: Float)
    fun onStop(velocity: Float)
}

/**
 * Drives this progress with a drag over [travel] px, upwards toward 1. A release settles at the
 * nearer end, or the way it was flung; [onSettle] hears the end it's headed for first. Animations
 * run in [scope], not the dragged node's, which may leave composition before they finish.
 */
@Composable
internal fun GestureProgress.rememberDragTarget(
    scope: CoroutineScope,
    travel: () -> Float,
    onSettle: (target: Float) -> Unit = {},
): DragTarget {
    val currentTravel by rememberUpdatedState(travel)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val progress = this
    return remember(progress, scope) {
        object : DragTarget {
            override suspend fun onStart() = progress.stopAnimation()

            override fun onDelta(delta: Float) = progress.snapTo(progress.value - delta / currentTravel())

            override fun onStop(velocity: Float) {
                val target = when {
                    velocity < -FLING_VELOCITY -> 1f
                    velocity > FLING_VELOCITY -> 0f
                    progress.value > 0.5f -> 1f
                    else -> 0f
                }
                currentOnSettle(target)
                scope.launch { progress.animateTo(target, initialVelocity = -velocity / currentTravel()) }
            }
        }
    }
}

/**
 * A vertical drag handed to [up] or [down] by the way it first moves; when one is null the other
 * takes both ways. Both are caught on touch, so whatever is animating stops under the finger, and
 * the one that wasn't dragged settles back on release.
 */
@Composable
internal fun rememberVerticalDrag(up: DragTarget?, down: DragTarget?, enabled: Boolean = true): Modifier {
    val currentUp by rememberUpdatedState(up)
    val currentDown by rememberUpdatedState(down)
    val route = remember { DragRoute() }
    val state = rememberDraggableState { delta ->
        val target = route.active
            ?: (if (delta < 0f) currentUp ?: currentDown else currentDown ?: currentUp).also { route.active = it }
        target?.onDelta(delta)
    }
    return Modifier.draggable(
        state = state,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStarted = {
            route.active = null
            route.caught = listOfNotNull(currentUp, currentDown).distinct()
            route.caught.forEach { it.onStart() }
        },
        onDragStopped = { velocity ->
            val active = route.active
            route.caught.forEach { if (it !== active) it.onStop(0f) }
            active?.onStop(velocity)
            route.active = null
            route.caught = emptyList()
        },
    )
}

private class DragRoute {
    var active: DragTarget? = null
    var caught: List<DragTarget> = emptyList()
}
