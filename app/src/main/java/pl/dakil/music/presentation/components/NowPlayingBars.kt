package pl.dakil.music.presentation.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BarPeriodsMs = intArrayOf(520, 410, 610)

/**
 * Three bouncing bars marking the song that is playing in a list. They dance while [isPlaying]
 * and settle into a calm, low pose when paused, so a paused current song still reads as current.
 */
@Composable
fun NowPlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingBars")
    val heights = BarPeriodsMs.map { period ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = bounce(period),
            label = "bar",
        )
    }
    val motion by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "barsMotion",
    )
    val restingHeights = floatArrayOf(0.45f, 0.75f, 0.3f)
    Canvas(modifier.size(size)) {
        val gap = this.size.width / 8f
        val barWidth = (this.size.width - gap * 2) / 3f
        heights.forEachIndexed { index, live ->
            val fraction = restingHeights[index] + (live.value - restingHeights[index]) * motion
            val barHeight = this.size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private fun bounce(periodMs: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse)
