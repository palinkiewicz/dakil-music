package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol

/**
 * A symbol sitting inside an expressive MaterialShapes polygon (settings rows, card headers, empty
 * states). [containerRotation] turns only the polygon; the symbol stays upright.
 */
@Composable
fun ShapedIcon(
    symbol: AppSymbol,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    filled: Boolean = true,
    contentDescription: String? = null,
    containerRotation: Float = 0f,
) {
    val shape = remember(polygon) { PolygonShape(polygon) }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = containerRotation }.background(containerColor, shape))
        AppIcon(
            symbol = symbol,
            contentDescription = contentDescription,
            filled = filled,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
