package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.AudioEffectsSettings
import pl.dakil.music.ui.designsystem.AppSlider
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Material 3 bottom sheet exposing the equalizer: a master switch, presets,
 * per-band sliders, and bass-boost strength. Reads everything from
 * [EqualizerUiState]; all changes are persisted by the caller (the ViewModel).
 */
@Composable
fun EqualizerSheet(
    state: EqualizerUiState,
    onDismiss: () -> Unit,
    onMasterEnabledChange: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevel: (index: Int, levelMb: Int) -> Unit,
    onBassBoost: (strength: Int) -> Unit,
    onReset: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ShapedIcon(
                    symbol = AppSymbols.Equalizer,
                    polygon = MaterialShapes.Cookie9Sided,
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.equalizer_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.settings.masterEnabled,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onMasterEnabledChange(it)
                    },
                    enabled = state.capabilities.available,
                    thumbContent = {
                        AppIcon(
                            if (state.settings.masterEnabled) AppSymbols.Check else AppSymbols.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                )
            }

            if (!state.capabilities.available) {
                Text(
                    text = stringResource(R.string.eq_not_supported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.l),
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            val enabled = state.settings.masterEnabled
            // Dim the controls when the master switch is off (they stay non-interactive too).
            val controlsAlpha by animateFloatAsState(
                targetValue = if (enabled) 1f else 0.38f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "eqAlpha",
            )

            PresetChips(
                presetNames = state.capabilities.presetNames,
                selectedPreset = state.settings.preset,
                enabled = enabled,
                onSelectPreset = {
                    haptics.tick()
                    onSelectPreset(it)
                },
                modifier = Modifier.alpha(controlsAlpha),
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.alpha(controlsAlpha),
            ) {
                BandSliders(
                    levelsMb = state.effectiveBandLevels,
                    centerFreqsMilliHz = state.capabilities.centerFreqsMilliHz,
                    minLevelMb = state.capabilities.minLevelMb,
                    maxLevelMb = state.capabilities.maxLevelMb,
                    enabled = enabled,
                    onBandLevel = onBandLevel,
                    modifier = Modifier.padding(vertical = Spacing.l),
                )
            }

            AnimatedVisibility(state.capabilities.bassBoostSupported) {
                StrengthSlider(
                    label = stringResource(R.string.eq_bass_boost),
                    strength = state.settings.bassBoostStrength,
                    enabled = enabled,
                    onChange = onBassBoost,
                    modifier = Modifier.alpha(controlsAlpha),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        onReset()
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    AppIcon(AppSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.eq_reset_flat))
                }
            }
        }
    }
}

@Composable
private fun PresetChips(
    presetNames: List<String>,
    selectedPreset: Int,
    enabled: Boolean,
    onSelectPreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = listOf(AudioEffectsSettings.PRESET_CUSTOM to stringResource(R.string.eq_preset_custom)) +
        presetNames.mapIndexed { index, name -> index to name }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        presets.forEach { (index, name) ->
            val selected = selectedPreset == index
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onSelectPreset(index) },
                label = { Text(name) },
                enabled = enabled,
                leadingIcon = if (selected) {
                    { AppIcon(AppSymbols.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.large,
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
                val level = levelsMb.getOrElse(band) { 0 }
                Text(
                    text = formatGain(level),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = if (level != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                BandSlider(
                    levelMb = level,
                    minLevelMb = minLevelMb,
                    maxLevelMb = maxLevelMb,
                    enabled = enabled,
                    onChange = { onBandLevel(band, it) },
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    text = formatFrequency(centerFreqsMilliHz[band]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One band as an M3 vertical slider (max at the top) that ticks as it crosses 0 dB. */
@Composable
private fun BandSlider(
    levelMb: Int,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val state = remember(minLevelMb, maxLevelMb) {
        SliderState(levelMb.toFloat(), trackRange = minLevelMb.toFloat()..maxLevelMb.toFloat())
    }
    state.value = levelMb.toFloat()
    VerticalSlider(
        state = state,
        onValueChange = { value ->
            if (value.sign != state.value.sign) haptics.tick()
            state.value = value
            onChange(value.roundToInt())
        },
        enabled = enabled,
        topToBottom = false,
        modifier = Modifier.height(180.dp),
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
            Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${strength / 10}%",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AppSlider(
            value = strength.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..AudioEffectsSettings.STRENGTH_MAX.toFloat(),
            enabled = enabled,
        )
    }
}

/** A band's gain in dB with an explicit sign, e.g. "+3", "0", "-1.5". */
private fun formatGain(levelMb: Int): String {
    val db = levelMb / 100f
    val text = if (db == db.toInt().toFloat()) db.toInt().toString() else "%.1f".format(db)
    return if (levelMb > 0) "+$text" else text
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
