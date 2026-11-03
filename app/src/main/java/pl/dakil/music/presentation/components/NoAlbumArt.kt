package pl.dakil.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import pl.dakil.music.ui.designsystem.PolygonShape
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/**
 * The cover of the synthetic "No album" entry, which has no artwork of its own: a tonal tile with
 * a few loose shapes drifting around a centred badge — songs that belong to no record. Scales with
 * whatever size it's given.
 */
@Composable
fun NoAlbumArt(shape: Shape, modifier: Modifier = Modifier) {
    val pill = remember { PolygonShape(MaterialShapes.Pill) }
    val burst = remember { PolygonShape(MaterialShapes.SoftBurst) }
    val clover = remember { PolygonShape(MaterialShapes.Clover4Leaf) }
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        // Loose shapes scattered toward the corners.
        Box(
            Modifier
                .size(side * 0.34f)
                .align(Alignment.TopStart)
                .offset(x = -side * 0.06f, y = -side * 0.04f)
                .graphicsLayer { rotationZ = -24f }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), pill),
        )
        Box(
            Modifier
                .size(side * 0.28f)
                .align(Alignment.BottomEnd)
                .offset(x = side * 0.05f, y = side * 0.05f)
                .graphicsLayer { rotationZ = 18f }
                .background(MaterialTheme.colorScheme.tertiaryContainer, burst),
        )
        Box(
            Modifier
                .size(side * 0.16f)
                .align(Alignment.TopEnd)
                .offset(x = -side * 0.14f, y = side * 0.12f)
                .background(MaterialTheme.colorScheme.primaryContainer, clover),
        )
        ShapedIcon(
            symbol = AppSymbols.FolderOff,
            polygon = MaterialShapes.Cookie9Sided,
            size = side * 0.46f,
            iconSize = side * 0.2f,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
