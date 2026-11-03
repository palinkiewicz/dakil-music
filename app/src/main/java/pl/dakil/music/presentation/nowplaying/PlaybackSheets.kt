package pl.dakil.music.presentation.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.SleepTimerMode
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.ui.designsystem.AppSlider
import pl.dakil.music.ui.designsystem.ConnectedToggleRow
import pl.dakil.music.ui.designsystem.RollingNumber
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.ToggleOption
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import kotlin.math.roundToInt

/** Speeds offered as one-tap presets. */
private val SPEED_PRESETS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Bounds for the fine speed slider; mirror the clamp in the player repository. */
private const val SPEED_MIN = 0.25f
private const val SPEED_MAX = 3.0f

/** Sleep-timer presets (minutes). */
private val SLEEP_PRESETS_MIN = listOf(5, 15, 30, 45, 60, 90)

/** Bounds for the custom sleep-timer slider (minutes). */
private const val SLEEP_MIN_MINUTES = 1
private const val SLEEP_MAX_MINUTES = 180

/** Formats a speed as a compact multiplier label, e.g. "1×" or "1.5×". */
internal fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) {
        speed.toInt().toString()
    } else {
        speed.toString().trimEnd('0').trimEnd('.')
    }
    return "${text}×"
}

/** The shared frame of the player's small sheets: a shaped icon, a big title and the body. */
@Composable
private fun PlayerSheet(
    symbol: AppSymbol,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ShapedIcon(
                symbol = symbol,
                polygon = MaterialShapes.Cookie9Sided,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
            content()
        }
    }
}

/** Playback speed: one-tap presets plus a fine slider in 0.05× steps. */
@Composable
fun SpeedSheet(
    speed: Float,
    onSetSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var value by remember { mutableFloatStateOf(speed.coerceIn(SPEED_MIN, SPEED_MAX)) }
    // Snap to 0.05 increments so the label stays clean.
    val snapped = (value * 20f).roundToInt() / 20f

    PlayerSheet(symbol = AppSymbols.Speed, title = stringResource(R.string.speed_title), onDismiss = onDismiss) {
        RollingNumber(
            text = formatSpeed(snapped),
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        ConnectedToggleRow(
            options = SPEED_PRESETS.map { ToggleOption(it, formatSpeed(it)) },
            selected = SPEED_PRESETS.firstOrNull { it == snapped } ?: -1f,
            onSelect = {
                value = it
                onSetSpeed(it)
            },
        )
        // Continuous (55 drawn stops would be a dotted mess) but snapped to 0.05×, with a detent
        // haptic each time the snapped value moves.
        AppSlider(
            value = value,
            onValueChange = {
                if ((it * 20f).roundToInt() != (value * 20f).roundToInt()) haptics.frequentTick()
                value = it
            },
            onValueChangeFinished = { onSetSpeed(snapped) },
            valueRange = SPEED_MIN..SPEED_MAX,
        )
    }
}

/** Sleep timer: presets, a custom duration, end-of-track/queue, and the live countdown. */
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    mode: SleepTimerMode?,
    onStart: (durationMs: Long) -> Unit,
    onEndOfTrack: () -> Unit,
    onEndOfQueue: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val active = remainingMs != null || mode != null
    var customMinutes by remember {
        mutableFloatStateOf((remainingMs?.let { (it / 60_000L).toInt() } ?: 30).coerceIn(SLEEP_MIN_MINUTES, SLEEP_MAX_MINUTES).toFloat())
    }

    PlayerSheet(symbol = AppSymbols.Bedtime, title = stringResource(R.string.sleep_timer_title), onDismiss = onDismiss) {
        if (active) {
            when {
                remainingMs != null -> RollingNumber(
                    text = formatDuration(remainingMs),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    stringResource(
                        if (mode == SleepTimerMode.END_OF_TRACK) R.string.sleep_end_of_track else R.string.sleep_end_of_queue,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = {
                    haptics.confirm()
                    onCancel()
                    onDismiss()
                },
            ) {
                AppIcon(AppSymbols.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.sleep_timer_off))
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SLEEP_PRESETS_MIN.forEach { minutes ->
                FilledTonalButton(
                    onClick = {
                        haptics.confirm()
                        onStart(minutes * 60_000L)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.sleep_timer_minutes, minutes))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ToggleButton(
                checked = mode == SleepTimerMode.END_OF_TRACK,
                onCheckedChange = {
                    haptics.confirm()
                    onEndOfTrack()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = FilledTonalToggleButtonDefaults.colors(),
            ) { Text(stringResource(R.string.sleep_end_of_track_short), maxLines = 1) }
            ToggleButton(
                checked = mode == SleepTimerMode.END_OF_QUEUE,
                onCheckedChange = {
                    haptics.confirm()
                    onEndOfQueue()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = FilledTonalToggleButtonDefaults.colors(),
            ) { Text(stringResource(R.string.sleep_end_of_queue_short), maxLines = 1) }
        }

        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.sleep_timer_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                AppSlider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = SLEEP_MIN_MINUTES.toFloat()..SLEEP_MAX_MINUTES.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        haptics.confirm()
                        onStart(customMinutes.roundToInt() * 60_000L)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.sleep_timer_minutes, customMinutes.roundToInt()))
                }
            }
        }
    }
}
