package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A value-driven M3 slider (the value overload is deprecated in material3 1.5) that ticks a haptic
 * on every discrete step, so stepped settings feel like detents under the finger.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    Slider(
        state = state,
        onValueChange = {
            if (steps > 0 && it != state.value) haptics.tick()
            state.value = it
            onValueChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        track = track ?: { SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = it) },
    )
}
