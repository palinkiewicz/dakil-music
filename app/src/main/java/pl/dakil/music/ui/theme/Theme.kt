package pl.dakil.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import pl.dakil.music.domain.model.AppColorTheme
import pl.dakil.music.domain.model.DarkThemeOption

/** Whether the resolved app theme is dark, which can differ from the system setting. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Whether dark surfaces are pure black (OLED); art-derived player schemes honour it too. */
val LocalPureBlack = staticCompositionLocalOf { false }

/** Whether the player takes its colors from the current cover art. */
val LocalPlayerArtColors = staticCompositionLocalOf { true }

// Pure black variant for OLED screens, applied on top of any dark scheme.
internal fun ColorScheme.toPureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF232323),
)

/**
 * Resolves the [ColorScheme] for a given color theme and dark flag.
 * Also used by the theme picker to paint its swatches.
 */
@Composable
fun colorSchemeFor(colorTheme: AppColorTheme, darkTheme: Boolean): ColorScheme {
    return when (colorTheme) {
        AppColorTheme.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DakilsMusicDark else DakilsMusicLight
            }
        }
        AppColorTheme.DAKILS_MUSIC -> if (darkTheme) DakilsMusicDark else DakilsMusicLight
        AppColorTheme.OCEAN -> if (darkTheme) OceanDark else OceanLight
        AppColorTheme.LAVENDER -> if (darkTheme) LavenderDark else LavenderLight
        AppColorTheme.SUNSET -> if (darkTheme) SunsetDark else SunsetLight
        AppColorTheme.ROSE -> if (darkTheme) RoseDark else RoseLight
        AppColorTheme.TEAL -> if (darkTheme) TealDark else TealLight
    }
}

/** Swallows every haptic request: what the app provides while the user has haptics turned off. */
private object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@Composable
fun MusicTheme(
    colorTheme: AppColorTheme = AppColorTheme.DAKILS_MUSIC,
    darkThemeOption: DarkThemeOption = DarkThemeOption.FOLLOW_SYSTEM,
    pureBlack: Boolean = false,
    hapticsEnabled: Boolean = true,
    playerArtColors: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkThemeOption) {
        DarkThemeOption.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeOption.LIGHT -> false
        DarkThemeOption.DARK -> true
    }

    val colorScheme = colorSchemeFor(colorTheme, darkTheme).let {
        if (darkTheme && pureBlack) it.toPureBlack() else it
    }

    // Keep the system bar icon contrast in sync with the app theme, which can
    // differ from the system dark mode setting.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Every haptic in the app goes through LocalHapticFeedback, so swapping it here is the one
    // switch that silences the app's own calls and the Material components' alike.
    val haptics = if (hapticsEnabled) LocalHapticFeedback.current else NoHapticFeedback
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalPureBlack provides (darkTheme && pureBlack),
        LocalPlayerArtColors provides playerArtColors,
        LocalHapticFeedback provides haptics,
    ) {
        MaterialExpressiveTheme(
            colorScheme = animateColorScheme(colorScheme),
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Cross-fades every role so switching theme, dark mode or pure black glides instead of snapping. */
@Composable
internal fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec: AnimationSpec<Color> = tween(durationMillis = 450)

    @Composable
    fun Color.animated(): Color = animateColorAsState(this, spec, label = "scheme").value

    return target.copy(
        primary = target.primary.animated(),
        onPrimary = target.onPrimary.animated(),
        primaryContainer = target.primaryContainer.animated(),
        onPrimaryContainer = target.onPrimaryContainer.animated(),
        inversePrimary = target.inversePrimary.animated(),
        secondary = target.secondary.animated(),
        onSecondary = target.onSecondary.animated(),
        secondaryContainer = target.secondaryContainer.animated(),
        onSecondaryContainer = target.onSecondaryContainer.animated(),
        tertiary = target.tertiary.animated(),
        onTertiary = target.onTertiary.animated(),
        tertiaryContainer = target.tertiaryContainer.animated(),
        onTertiaryContainer = target.onTertiaryContainer.animated(),
        background = target.background.animated(),
        onBackground = target.onBackground.animated(),
        surface = target.surface.animated(),
        onSurface = target.onSurface.animated(),
        surfaceVariant = target.surfaceVariant.animated(),
        onSurfaceVariant = target.onSurfaceVariant.animated(),
        surfaceTint = target.surfaceTint.animated(),
        inverseSurface = target.inverseSurface.animated(),
        inverseOnSurface = target.inverseOnSurface.animated(),
        error = target.error.animated(),
        onError = target.onError.animated(),
        errorContainer = target.errorContainer.animated(),
        onErrorContainer = target.onErrorContainer.animated(),
        outline = target.outline.animated(),
        outlineVariant = target.outlineVariant.animated(),
        surfaceBright = target.surfaceBright.animated(),
        surfaceDim = target.surfaceDim.animated(),
        surfaceContainer = target.surfaceContainer.animated(),
        surfaceContainerHigh = target.surfaceContainerHigh.animated(),
        surfaceContainerHighest = target.surfaceContainerHighest.animated(),
        surfaceContainerLow = target.surfaceContainerLow.animated(),
        surfaceContainerLowest = target.surfaceContainerLowest.animated(),
    )
}
