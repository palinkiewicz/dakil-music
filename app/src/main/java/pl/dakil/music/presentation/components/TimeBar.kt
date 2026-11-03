package pl.dakil.music.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.dakil.music.ui.designsystem.AppSlider
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.tabular

/**
 * Seek bar with elapsed/total labels, shared by the player and the quick player. The track is an
 * expressive wavy line that ripples while [isPlaying] and flattens when paused or grabbed, so the
 * bar itself says whether music is moving.
 */
@Composable
fun TimeBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val haptics = rememberHaptics()
    // While dragging we show the in-progress value; otherwise the live position.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue = dragValue ?: positionMs.toFloat().coerceIn(0f, duration)

    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val grabbed = dragged || pressed
    LaunchedEffect(grabbed) { if (grabbed) haptics.tick() }

    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying && !grabbed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "seekAmplitude",
    )
    val colors = SliderDefaults.colors(thumbColor = activeColor, activeTrackColor = activeColor)

    Column(modifier = modifier.fillMaxWidth()) {
        AppSlider(
            value = sliderValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..duration,
            colors = colors,
            interactionSource = interaction,
            track = { state ->
                val range = state.trackRange
                val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                LinearWavyProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = activeColor,
                    trackColor = inactiveColor,
                    amplitude = { amplitude },
                    waveSpeed = if (isPlaying && !grabbed) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelStyle = MaterialTheme.typography.labelMedium.tabular()
            Text(
                text = formatDuration(sliderValue.toLong()),
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
