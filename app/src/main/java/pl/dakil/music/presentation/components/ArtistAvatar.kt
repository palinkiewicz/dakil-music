package pl.dakil.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.music.ui.designsystem.PolygonShape

/** The polygons an avatar can take; a name always maps to the same one. */
private val AvatarPolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Flower,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.SoftBurst,
    MaterialShapes.Gem,
)

/** A stable, well-spread index for [name] (String.hashCode clusters for similar names). */
internal fun stableIndex(name: String, size: Int): Int {
    var h = 1125899906842597L
    name.lowercase().forEach { h = 31 * h + it.code }
    return ((h xor (h ushr 29)) and Long.MAX_VALUE).rem(size).toInt()
}

/** A container/content color pair for [name], from the scheme's three accent containers. */
@Composable
fun tonalPairFor(name: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (stableIndex(name, 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

/**
 * An artist's avatar: their initials inside an expressive MaterialShapes polygon. The shape and
 * tone are derived from the name, so every artist keeps a recognisable look across the app.
 */
@Composable
fun ArtistAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMediumEmphasized,
) {
    val shape = remember(name) { PolygonShape(AvatarPolygons[stableIndex(name, AvatarPolygons.size)]) }
    val (container, content) = tonalPairFor(name)
    Box(
        modifier = modifier
            .size(size)
            .background(container, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = textStyle, color = content, maxLines = 1)
    }
}

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> name.take(1).uppercase()
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
