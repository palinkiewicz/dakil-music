package pl.dakil.music.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Lets a child reach past its parent's horizontal padding by [amount] on each side (an edge-to-edge
 * scrolling row inside a padded page), while still taking only the parent's width in layout.
 */
fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
