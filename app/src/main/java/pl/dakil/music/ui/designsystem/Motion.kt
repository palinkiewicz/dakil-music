package pl.dakil.music.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STAGGER_MS = 45L
private const val MAX_STAGGERED = 6

/**
 * Rises and fades an item in when it first composes, [index] steps after the previous one, so a
 * page of cards cascades in rather than popping. Draw-phase only: layout never moves.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS)
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 40.dp.toPx()
    }
}
