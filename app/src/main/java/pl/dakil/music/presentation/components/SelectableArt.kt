package pl.dakil.music.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.music.ui.designsystem.MorphShape
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/**
 * A list row's leading art that doubles as its selection checkbox: when [selected] the art shrinks
 * back and a primary Cookie blooms over it with a check, morphing from a square as it grows.
 */
@Composable
fun SelectableArt(
    model: Any?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "selectArt",
    )
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        AlbumArt(
            model = model,
            shape = shape,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.15f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.6f * progress
                },
        )
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 0.5f + 0.5f * progress
                        scaleY = 0.5f + 0.5f * progress
                        alpha = progress.coerceIn(0f, 1f)
                        rotationZ = 90f * (1f - progress)
                    }
                    .background(MaterialTheme.colorScheme.primary, MorphShape(morph, progress.coerceIn(0f, 1f))),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    AppSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}
