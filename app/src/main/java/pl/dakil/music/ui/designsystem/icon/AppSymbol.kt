package pl.dakil.music.ui.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** A bundled Material Symbol (Rounded) with its outlined and filled variants. */
@Immutable
data class AppSymbol(@DrawableRes val outlined: Int, @DrawableRes val filled: Int)

@Composable
fun AppIcon(
    symbol: AppSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(if (filled) symbol.filled else symbol.outlined),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/** An [AppIcon] that cross-fades between its outlined and filled variants (selection states). */
@Composable
fun AnimatedAppIcon(
    symbol: AppSymbol,
    contentDescription: String?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Crossfade(
        targetState = filled,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        modifier = modifier,
        label = "symbolFill",
    ) { isFilled ->
        AppIcon(symbol, contentDescription, filled = isFilled, tint = tint)
    }
}
