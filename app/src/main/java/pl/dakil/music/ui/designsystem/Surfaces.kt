package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import pl.dakil.music.ui.theme.SegmentedShapes

/** Segmented list items as visible tonal tiles on the app's surface. */
@Composable
fun segmentedItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/**
 * Tiles whose state is shown by a control inside them (a switch): checking them must not also
 * recolour the whole tile, or a screen of enabled settings turns into a wall of accent.
 */
@Composable
fun checkableItemColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * The shapes of a tile inside a [SegmentedGroup]: small corners everywhere, the group's clip turns
 * the outer corners of its first and last tiles into the big rounded ones. A pressed tile still
 * rounds; a checked one keeps its shape, since its switch already shows the state.
 */
@Composable
fun segmentedInnerShapes(): ListItemShapes = ListItemDefaults.segmentedShapes(index = 1, count = 3).let {
    it.copy(selectedShape = it.shape)
}

/**
 * A group of segmented tiles reading as one rounded card: children touch with a hairline gap, and
 * the clip rounds the group's outer corners whatever (and however many) rows end up inside.
 */
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(SegmentedShapes.group),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        content = content,
    )
}

/** Toggle buttons that stay visible on container-colored backgrounds (cards, scrolled app bars). */
@Composable
fun raisedToggleColors(): ToggleButtonColors =
    ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
