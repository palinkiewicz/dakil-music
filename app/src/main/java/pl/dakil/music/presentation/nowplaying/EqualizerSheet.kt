package pl.dakil.music.presentation.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.AudioEffectsSettings
import kotlin.math.roundToInt

/**
 * Material 3 bottom sheet exposing the equalizer: a master switch, device presets,
 * per-band sliders, and bass-boost / virtualizer strength. Reads everything from
 * [EqualizerUiState]; all changes are persisted by the caller (the ViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    state: EqualizerUiState,
    onDismiss: () -> Unit,
    onMasterEnabledChange: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevel: (index: Int, levelMb: Int) -> Unit,
    onBassBoost: (strength: Int) -> Unit,
    onVirtualizer: (strength: Int) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.equalizer_title),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.settings.masterEnabled,
                    onCheckedChange = onMasterEnabledChange,
                )
            }

            if (!state.capabilities.available) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.eq_not_supported),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            val enabled = state.settings.masterEnabled
            // Dim the controls when the master switch is off (they stay non-interactive too).
            val controlsAlpha = if (enabled) 1f else 0.38f

            Spacer(Modifier.height(16.dp))

            PresetChips(
                presetNames = state.capabilities.presetNames,
                selectedPreset = state.settings.preset,
                enabled = enabled,
                onSelectPreset = onSelectPreset,
                modifier = Modifier.alpha(controlsAlpha),
            )

            Spacer(Modifier.height(24.dp))

            BandSliders(
                levelsMb = state.effectiveBandLevels,
                centerFreqsMilliHz = state.capabilities.centerFreqsMilliHz,
                minLevelMb = state.capabilities.minLevelMb,
                maxLevelMb = state.capabilities.maxLevelMb,
                enabled = enabled,
                onBandLevel = onBandLevel,
                modifier = Modifier.alpha(controlsAlpha),
            )

            if (state.capabilities.bassBoostSupported) {
                Spacer(Modifier.height(16.dp))
                StrengthSlider(
                    label = stringResource(R.string.eq_bass_boost),
                    strength = state.settings.bassBoostStrength,
                    enabled = enabled,
                    onChange = onBassBoost,
                    modifier = Modifier.alpha(controlsAlpha),
                )
            }

            if (state.capabilities.virtualizerSupported) {
                Spacer(Modifier.height(8.dp))
                StrengthSlider(
                    label = stringResource(R.string.eq_virtualizer),
                    strength = state.settings.virtualizerStrength,
                    enabled = enabled,
                    onChange = onVirtualizer,
                    modifier = Modifier.alpha(controlsAlpha),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset, enabled = enabled) {
                    Text(stringResource(R.string.eq_reset_flat))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    presetNames: List<String>,
    selectedPreset: Int,
    enabled: Boolean,
    onSelectPreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedPreset == AudioEffectsSettings.PRESET_CUSTOM,
            onClick = { onSelectPreset(AudioEffectsSettings.PRESET_CUSTOM) },
            label = { Text(stringResource(R.string.eq_preset_custom)) },
            enabled = enabled,
        )
        presetNames.forEachIndexed { index, name ->
            FilterChip(
                selected = selectedPreset == index,
                onClick = { onSelectPreset(index) },
                label = { Text(name) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun BandSliders(
    levelsMb: List<Int>,
    centerFreqsMilliHz: List<Int>,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onBandLevel: (index: Int, levelMb: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (band in centerFreqsMilliHz.indices) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .height(160.dp)
                        .width(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VerticalSlider(
                        value = levelsMb.getOrElse(band) { 0 }.toFloat(),
                        onValueChange = { onBandLevel(band, it.roundToInt()) },
                        valueRange = minLevelMb.toFloat()..maxLevelMb.toFloat(),
                        enabled = enabled,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatFrequency(centerFreqsMilliHz[band]),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A [Slider] rotated to run bottom-to-top, sized to fill its (already sized) parent box. */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = Modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                // Measure the slider with width/height swapped, then rotate it into place
                // so its (long) track runs along the parent box's height.
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            },
    )
}

@Composable
private fun StrengthSlider(
    label: String,
    strength: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.weight(1f))
            Text(
                text = "${strength / 10}%",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = strength.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..AudioEffectsSettings.STRENGTH_MAX.toFloat(),
            enabled = enabled,
        )
    }
}

/** Formats a center frequency given in milli-hertz as a compact label (e.g. "60Hz", "3.2kHz"). */
private fun formatFrequency(milliHz: Int): String {
    val hz = milliHz / 1000
    return if (hz >= 1000) {
        val k = hz / 1000f
        if (k == k.toInt().toFloat()) "${k.toInt()}kHz" else "%.1fkHz".format(k)
    } else {
        "${hz}Hz"
    }
}
