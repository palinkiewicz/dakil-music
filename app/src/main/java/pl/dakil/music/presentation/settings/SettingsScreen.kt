package pl.dakil.music.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.queueRemoveModeNameRes
import pl.dakil.music.presentation.components.statDefaultRangeNameRes
import pl.dakil.music.presentation.components.statMetricNameRes
import pl.dakil.music.presentation.components.weekdayNameRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        // The host already insets for the bottom navigation bar; don't add it twice.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            SwitchRow(
                title = stringResource(R.string.settings_theme),
                summary = stringResource(R.string.settings_theme_summary),
                checked = settings.forceDarkTheme,
                onCheckedChange = viewModel::setForceDarkTheme,
            )
            SwitchRow(
                title = stringResource(R.string.settings_gapless),
                summary = stringResource(R.string.settings_gapless_summary),
                checked = settings.gaplessPlayback,
                onCheckedChange = viewModel::setGaplessPlayback,
            )
            SwitchRow(
                title = stringResource(R.string.settings_remember_sort),
                summary = stringResource(R.string.settings_remember_sort_summary),
                checked = settings.rememberSortState,
                onCheckedChange = viewModel::setRememberSortState,
            )
            SliderRow(
                title = stringResource(R.string.settings_album_columns),
                summary = stringResource(R.string.settings_album_columns_summary),
                value = settings.albumColumns,
                onValueChange = viewModel::setAlbumColumns,
                valueRange = 1..4,
            )
            SelectRow(
                title = stringResource(R.string.settings_queue_remove_mode),
                summary = stringResource(R.string.settings_queue_remove_mode_summary),
                selectedLabel = stringResource(queueRemoveModeNameRes(settings.queueRemoveMode)),
                options = QueueRemoveMode.entries.map { it to stringResource(queueRemoveModeNameRes(it)) },
                onSelect = viewModel::setQueueRemoveMode,
            )

            SectionHeader(stringResource(R.string.settings_section_statistics))

            var showDisableDialog by remember { mutableStateOf(false) }
            SwitchRow(
                title = stringResource(R.string.settings_statistics_enabled),
                summary = stringResource(R.string.settings_statistics_enabled_summary),
                checked = settings.statisticsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) viewModel.setStatisticsEnabled(true) else showDisableDialog = true
                },
            )
            if (showDisableDialog) {
                DisableStatisticsDialog(
                    onConfirm = {
                        viewModel.setStatisticsEnabled(false)
                        showDisableDialog = false
                    },
                    onDismiss = { showDisableDialog = false },
                )
            }
            val statsEnabled = settings.statisticsEnabled
            SliderRow(
                title = stringResource(R.string.settings_min_play_seconds),
                summary = stringResource(R.string.settings_min_play_seconds_summary),
                value = settings.minPlaySeconds,
                onValueChange = { viewModel.setMinPlaySeconds((it / 5) * 5) },
                valueRange = 0..30,
                steps = 5,
                valueLabel = { "${it}s" },
                enabled = statsEnabled,
            )
            val offLabel = stringResource(R.string.settings_history_update_off)
            SliderRow(
                title = stringResource(R.string.settings_history_update_seconds),
                summary = stringResource(R.string.settings_history_update_seconds_summary),
                value = settings.historyUpdateSeconds,
                onValueChange = viewModel::setHistoryUpdateSeconds,
                valueRange = 0..15,
                valueLabel = { if (it == 0) offLabel else "${it}s" },
                enabled = statsEnabled,
            )
            SelectRow(
                title = stringResource(R.string.settings_first_day_of_week),
                summary = stringResource(R.string.settings_first_day_of_week_summary),
                selectedLabel = stringResource(weekdayNameRes(settings.firstDayOfWeek)),
                options = (1..7).map { iso -> iso to stringResource(weekdayNameRes(iso)) },
                onSelect = viewModel::setFirstDayOfWeek,
                enabled = statsEnabled,
            )
            SelectRow(
                title = stringResource(R.string.settings_stats_default_range),
                summary = stringResource(R.string.settings_stats_default_range_summary),
                selectedLabel = stringResource(statDefaultRangeNameRes(settings.statsDefaultRange)),
                options = StatDefaultRange.entries.map { it to stringResource(statDefaultRangeNameRes(it)) },
                onSelect = viewModel::setStatsDefaultRange,
                enabled = statsEnabled,
            )
            SelectRow(
                title = stringResource(R.string.settings_stats_default_metric),
                summary = stringResource(R.string.settings_stats_default_metric_summary),
                selectedLabel = stringResource(statMetricNameRes(settings.statsDefaultMetric)),
                options = StatMetric.entries.map { it to stringResource(statMetricNameRes(it)) },
                onSelect = viewModel::setStatsDefaultMetric,
                enabled = statsEnabled,
            )
        }
    }
}

@Composable
private fun DisableStatisticsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_disable_stats_title)) },
        text = { Text(stringResource(R.string.settings_disable_stats_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_disable_stats_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp, end = 16.dp),
    )
}

/**
 * Shared base for every settings row, so switches, sliders and selects share the
 * exact same paddings and sizing (the "minimum listening time" look). [trailing]
 * is sized to its own content.
 */
@Composable
private fun SettingRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(summary)
                supporting?.invoke()
            }
        },
        trailingContent = trailing,
    )
}

@Composable
private fun <T> SelectRow(
    title: String,
    summary: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { expanded = true },
        trailing = {
            // Menu anchored to this trailing box so it opens on the right.
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SliderRow(
    title: String,
    summary: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    steps: Int = valueRange.last - valueRange.first - 1,
    valueLabel: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        supporting = {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                enabled = enabled,
            )
        },
        trailing = { Text(valueLabel(value)) },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

private const val DISABLED_ALPHA = 0.38f
