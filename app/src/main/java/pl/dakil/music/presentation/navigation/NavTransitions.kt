package pl.dakil.music.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Mirrors the expressive "default spatial" spring so navigation feels like the rest of the UI.
private val spatial = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 700f)

/**
 * Material 3 shared-axis X for drilling into and out of screens (the incoming screen travels a fifth
 * of the width, the outgoing one a tenth, both on a spring), and fade-through between tabs.
 */
internal object NavTransitions {
    fun pushEnter(): EnterTransition =
        slideInHorizontally(spatial) { it / 5 } + fadeIn(tween(220, 60, EmphasizedDecelerate))

    fun pushExit(): ExitTransition =
        slideOutHorizontally(spatial) { -it / 10 } + fadeOut(tween(120, easing = EmphasizedAccelerate))

    fun popEnter(): EnterTransition =
        slideInHorizontally(spatial) { -it / 10 } + fadeIn(tween(220, 60, EmphasizedDecelerate))

    fun popExit(): ExitTransition =
        slideOutHorizontally(spatial) { it / 5 } + fadeOut(tween(120, easing = EmphasizedAccelerate))

    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(210, 90, EmphasizedDecelerate)) +
            scaleIn(tween(210, 90, EmphasizedDecelerate), initialScale = 0.94f)

    fun fadeThroughExit(): ExitTransition = fadeOut(tween(90, easing = EmphasizedAccelerate))
}
