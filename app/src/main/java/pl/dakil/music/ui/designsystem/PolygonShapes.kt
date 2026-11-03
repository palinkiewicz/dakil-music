package pl.dakil.music.ui.designsystem

import androidx.compose.material3.toPath
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * A [Shape] for one point of a [Morph] between two MaterialShapes polygons. Cheap enough to create
 * every animation frame. Rotate with `graphicsLayer` at the call site.
 */
@Immutable
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.toPath(progress = progress, path = Path()).scaledTo(size))
}

/** A [Shape] for a single MaterialShapes polygon, scaled to fill its bounds. */
@Immutable
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(polygon.toPath(android.graphics.Path()).asComposePath().scaledTo(size))
}

// MaterialShapes polygons are normalized to the unit square.
private fun Path.scaledTo(size: Size): Path {
    transform(Matrix().apply { scale(size.width, size.height) })
    return this
}
