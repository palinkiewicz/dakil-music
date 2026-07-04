package pl.dakil.music.presentation.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AppColorTheme
import pl.dakil.music.domain.model.DarkThemeOption
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.albumAuthorModeNameRes
import pl.dakil.music.presentation.components.colorThemeNameRes
import pl.dakil.music.presentation.components.darkThemeOptionNameRes
import pl.dakil.music.presentation.components.queueRemoveModeNameRes
import pl.dakil.music.presentation.components.statDefaultRangeNameRes
import pl.dakil.music.presentation.components.statMetricNameRes
import pl.dakil.music.presentation.components.weekdayNameRes
import pl.dakil.music.ui.theme.colorSchemeFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAlbumRules: () -> Unit,
    onOpenNavigation: () -> Unit,
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
            SectionHeader(stringResource(R.string.settings_section_theme))
            val availableThemes = remember {
                AppColorTheme.entries.filter {
                    it != AppColorTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(availableThemes.size) { index ->
                    val theme = availableThemes[index]
                    ThemeColorSwatch(
                        theme = theme,
                        isSelected = theme == settings.colorTheme,
                        onClick = { viewModel.setColorTheme(theme) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_dark_mode))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                DarkThemeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == settings.darkThemeOption,
                        onClick = { viewModel.setDarkThemeOption(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DarkThemeOption.entries.size,
                        ),
                    ) {
                        Text(stringResource(darkThemeOptionNameRes(option)))
                    }
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_pure_black),
                summary = stringResource(R.string.settings_pure_black_summary),
                checked = settings.pureBlack,
                onCheckedChange = viewModel::setPureBlack,
                enabled = settings.darkThemeOption != DarkThemeOption.LIGHT,
            )

            SectionHeader(stringResource(R.string.settings_section_navigation))
            NavigationRow(
                title = stringResource(R.string.settings_navigation),
                summary = stringResource(R.string.settings_navigation_summary),
                onClick = onOpenNavigation,
            )

            SectionHeader(stringResource(R.string.settings_section_library))
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
            SwitchRow(
                title = stringResource(R.string.settings_album_shared_cover_art),
                summary = stringResource(R.string.settings_album_shared_cover_art_summary),
                checked = settings.albumCoverArtMode == AlbumCoverArtMode.SHARED,
                onCheckedChange = {
                    viewModel.setAlbumCoverArtMode(
                        if (it) AlbumCoverArtMode.SHARED else AlbumCoverArtMode.INDIVIDUAL,
                    )
                },
            )
            SelectRow(
                title = stringResource(R.string.settings_album_author_mode),
                summary = stringResource(R.string.settings_album_author_mode_summary),
                selectedLabel = stringResource(albumAuthorModeNameRes(settings.albumAuthorMode)),
                options = AlbumAuthorMode.entries.map { it to stringResource(albumAuthorModeNameRes(it)) },
                onSelect = viewModel::setAlbumAuthorMode,
            )
            SliderRow(
                title = stringResource(R.string.settings_album_corner_roundness),
                summary = stringResource(R.string.settings_album_corner_roundness_summary),
                value = settings.albumCornerRoundnessDp,
                onValueChange = { viewModel.setAlbumCornerRoundnessDp((it / 4) * 4) },
                valueRange = 0..64,
                steps = 15,
                valueLabel = { "${it}dp" },
            )
            NavigationRow(
                title = stringResource(R.string.settings_album_rules),
                summary = stringResource(R.string.settings_album_rules_summary),
                onClick = onOpenAlbumRules,
            )

            SectionHeader(stringResource(R.string.settings_section_now_playing))
            SliderRow(
                title = stringResource(R.string.settings_now_playing_corner_roundness),
                summary = stringResource(R.string.settings_now_playing_corner_roundness_summary),
                value = settings.nowPlayingCornerRoundnessDp,
                onValueChange = { viewModel.setNowPlayingCornerRoundnessDp((it / 4) * 4) },
                valueRange = 0..64,
                steps = 15,
                valueLabel = { "${it}dp" },
            )
            SwitchRow(
                title = stringResource(R.string.settings_gapless),
                summary = stringResource(R.string.settings_gapless_summary),
                checked = settings.gaplessPlayback,
                onCheckedChange = viewModel::setGaplessPlayback,
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_pause_zero_volume),
                summary = stringResource(R.string.settings_auto_pause_zero_volume_summary),
                checked = settings.autoPauseOnZeroVolume,
                onCheckedChange = viewModel::setAutoPauseOnZeroVolume,
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_resume_volume),
                summary = stringResource(R.string.settings_auto_resume_volume_summary),
                checked = settings.autoResumeOnVolumeRestored,
                onCheckedChange = viewModel::setAutoResumeOnVolumeRestored,
                enabled = settings.autoPauseOnZeroVolume,
            )
            SelectRow(
                title = stringResource(R.string.settings_queue_remove_mode),
                summary = stringResource(R.string.settings_queue_remove_mode_summary),
                selectedLabel = stringResource(queueRemoveModeNameRes(settings.queueRemoveMode)),
                options = QueueRemoveMode.entries.map { it to stringResource(queueRemoveModeNameRes(it)) },
                onSelect = viewModel::setQueueRemoveMode,
            )
            SwitchRow(
                title = stringResource(R.string.settings_display_lyrics),
                summary = stringResource(R.string.settings_display_lyrics_summary),
                checked = settings.displayLyrics,
                onCheckedChange = viewModel::setDisplayLyrics,
            )
            SwitchRow(
                title = stringResource(R.string.settings_fetch_missing_lyrics),
                summary = stringResource(R.string.settings_fetch_missing_lyrics_summary),
                checked = settings.fetchMissingLyricsFromLrclib,
                onCheckedChange = viewModel::setFetchMissingLyricsFromLrclib,
                enabled = settings.displayLyrics,
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
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** A row that opens another screen, marked with a trailing chevron. */
@Composable
private fun NavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun ThemeColorSwatch(
    theme: AppColorTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val scheme = colorSchemeFor(colorTheme = theme, darkTheme = isDark)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(if (isSelected) 6.dp else 0.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(scheme.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(scheme.secondaryContainer, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(scheme.tertiaryContainer, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(colorThemeNameRes(theme)),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
