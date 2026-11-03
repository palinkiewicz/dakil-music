package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import pl.dakil.music.ui.theme.SegmentedShapes

/**
 * The shape of one tile in a lazy list rendered as a segmented group: the first tile gets the
 * group's big top corners, the last its big bottom corners, the rest stay small, so a long lazy
 * list reads as one rounded card (the lazy counterpart of [SegmentedGroup]).
 */
fun segmentedItemShape(index: Int, count: Int): Shape {
    val outer = SegmentedShapes.outer
    val inner = SegmentedShapes.inner
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
}
