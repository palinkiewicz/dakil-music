package pl.dakil.music.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** The app-wide shared transition scope (set around the NavHost), or null outside of it. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The visibility scope of the current navigation destination, for shared elements. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as shared across navigation under [key] (e.g. an album's cover flying from
 * the grid into the album page), clipped to [shape] while it travels. A no-op outside a NavHost.
 */
@Composable
fun Modifier.sharedElement(key: Any, shape: Shape): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedScope.current ?: return this
    return with(transitionScope) {
        this@sharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
