package pl.dakil.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    largeIncreased = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** 8dp-grid spacing tokens. */
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Horizontal page gutter on phones. */
    val gutter: Dp = 16.dp

    /** Readable content width; wider windows center the content. */
    val maxContentWidth: Dp = 720.dp
}

/**
 * Expressive "segmented" grouping: items of one group touch with small inner corners and a hairline
 * gap, while the group as a whole reads as one big rounded card.
 */
object SegmentedShapes {
    val outer: Dp = 28.dp
    val inner: Dp = 6.dp
    val gap: Dp = 3.dp

    val group = RoundedCornerShape(outer)
    val item = RoundedCornerShape(inner)
}
