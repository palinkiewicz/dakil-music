package pl.dakil.music.presentation.settings

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
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
import pl.dakil.music.presentation.components.statDefaultRangeNameRes
import pl.dakil.music.presentation.components.statMetricNameRes
import pl.dakil.music.presentation.components.weekdayNameRes
import pl.dakil.music.ui.designsystem.ConnectedToggleRow
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.MorphShape
import pl.dakil.music.ui.designsystem.SegmentedGroup
import pl.dakil.music.ui.designsystem.ToggleOption
import pl.dakil.music.ui.designsystem.bleedHorizontally
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.pressScale
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.LocalDarkTheme
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.colorSchemeFor

/** The settings root: every category as a shaped-icon row that opens its own screen. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenNavigation: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsSubscreen(stringResource(R.string.settings_title), onBack) {
        SectionHeader(stringResource(R.string.settings_section_personalize))
        SegmentedGroup {
            NavigationRow(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                icon = AppSymbols.Palette,
                onClick = onOpenAppearance,
            )
            NavigationRow(
                title = stringResource(R.string.settings_navigation),
                summary = stringResource(R.string.settings_navigation_summary),
                icon = AppSymbols.DockToBottom,
                onClick = onOpenNavigation,
            )
        }
        SectionHeader(stringResource(R.string.settings_section_music))
        SegmentedGroup {
            NavigationRow(
                title = stringResource(R.string.settings_section_library),
                summary = stringResource(R.string.settings_library_summary),
                icon = AppSymbols.LibraryMusic,
                onClick = onOpenLibrary,
            )
            NavigationRow(
                title = stringResource(R.string.settings_playback),
                summary = stringResource(R.string.settings_playback_summary),
                icon = AppSymbols.PlayCircle,
                onClick = onOpenPlayback,
            )
        }
        SectionHeader(stringResource(R.string.settings_section_data))
        SegmentedGroup {
            NavigationRow(
                title = stringResource(R.string.settings_section_statistics),
                summary = stringResource(R.string.settings_statistics_summary),
                icon = AppSymbols.BarChart,
                badge = if (!settings.statisticsEnabled) stringResource(R.string.settings_off_badge) else null,
                onClick = onOpenStatistics,
            )
        }
    }
}

/** Theme colors, dark mode, pure black and the "feel" switches (haptics, art colors). */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    SettingsSubscreen(stringResource(R.string.settings_appearance), onBack) {
        SectionHeader(stringResource(R.string.settings_section_theme))
        val availableThemes = remember {
            AppColorTheme.entries.filter {
                it != AppColorTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            }
        }
        // Bleeds into the page gutters so swatches scroll edge to edge.
        LazyRow(
            modifier = Modifier.bleedHorizontally(Spacing.gutter),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(availableThemes) { theme ->
                ThemeColorSwatch(
                    theme = theme,
                    isSelected = theme == settings.colorTheme,
                    onClick = {
                        if (theme != settings.colorTheme) haptics.tick()
                        viewModel.setColorTheme(theme)
                    },
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_dark_mode))
        ConnectedToggleRow(
            options = listOf(
                ToggleOption(DarkThemeOption.FOLLOW_SYSTEM, stringResource(darkThemeOptionNameRes(DarkThemeOption.FOLLOW_SYSTEM)), AppSymbols.BrightnessAuto),
                ToggleOption(DarkThemeOption.LIGHT, stringResource(darkThemeOptionNameRes(DarkThemeOption.LIGHT)), AppSymbols.LightMode),
                ToggleOption(DarkThemeOption.DARK, stringResource(darkThemeOptionNameRes(DarkThemeOption.DARK)), AppSymbols.DarkMode),
            ),
            selected = settings.darkThemeOption,
            onSelect = viewModel::setDarkThemeOption,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_pure_black),
                summary = stringResource(R.string.settings_pure_black_summary),
                checked = settings.pureBlack,
                onCheckedChange = viewModel::setPureBlack,
                enabled = settings.darkThemeOption != DarkThemeOption.LIGHT,
            )
            SwitchRow(
                title = stringResource(R.string.settings_player_art_colors),
                summary = stringResource(R.string.settings_player_art_colors_summary),
                checked = settings.playerArtColors,
                onCheckedChange = viewModel::setPlayerArtColors,
            )
        }

        SectionHeader(stringResource(R.string.settings_section_feel))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                checked = settings.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )
        }
    }
}

/** Library grid, covers, album authorship, sorting memory and album rules. */
@Composable
fun LibrarySettingsScreen(
    onBack: () -> Unit,
    onOpenAlbumRules: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsSubscreen(stringResource(R.string.settings_section_library), onBack) {
        SectionHeader(stringResource(R.string.tab_albums))
        SegmentedGroup {
            SliderRow(
                title = stringResource(R.string.settings_album_columns),
                summary = stringResource(R.string.settings_album_columns_summary),
                value = settings.albumColumns,
                onValueChange = viewModel::setAlbumColumns,
                valueRange = 1..4,
                leading = { ColumnsPreview(settings.albumColumns) },
            )
            SliderRow(
                title = stringResource(R.string.settings_album_corner_roundness),
                summary = stringResource(R.string.settings_album_corner_roundness_summary),
                value = settings.albumCornerRoundnessDp,
                onValueChange = { viewModel.setAlbumCornerRoundnessDp((it / 4) * 4) },
                valueRange = 0..64,
                steps = 15,
                valueLabel = { "${it}dp" },
                leading = { CornerPreview(settings.albumCornerRoundnessDp) },
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
            NavigationRow(
                title = stringResource(R.string.settings_album_rules),
                summary = stringResource(R.string.settings_album_rules_summary),
                icon = AppSymbols.Rule,
                onClick = onOpenAlbumRules,
            )
        }
        SectionHeader(stringResource(R.string.settings_section_sorting))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_remember_sort),
                summary = stringResource(R.string.settings_remember_sort_summary),
                checked = settings.rememberSortState,
                onCheckedChange = viewModel::setRememberSortState,
            )
        }
    }
}

/** The player's look, how the queue removes songs, playback behaviour and lyrics. */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsSubscreen(stringResource(R.string.settings_playback), onBack) {
        SectionHeader(stringResource(R.string.settings_section_now_playing))
        SegmentedGroup {
            SliderRow(
                title = stringResource(R.string.settings_now_playing_corner_roundness),
                summary = stringResource(R.string.settings_now_playing_corner_roundness_summary),
                value = settings.nowPlayingCornerRoundnessDp,
                onValueChange = { viewModel.setNowPlayingCornerRoundnessDp((it / 4) * 4) },
                valueRange = 0..64,
                steps = 15,
                valueLabel = { "${it}dp" },
                leading = { CornerPreview(settings.nowPlayingCornerRoundnessDp) },
            )
        }

        SectionHeader(stringResource(R.string.settings_queue_remove_mode))
        Text(
            stringResource(R.string.settings_queue_remove_mode_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.s),
        )
        ConnectedToggleRow(
            options = QueueRemoveMode.entries.map {
                ToggleOption(
                    it,
                    stringResource(
                        when (it) {
                            QueueRemoveMode.MENU -> R.string.queue_remove_short_menu
                            QueueRemoveMode.BUTTON -> R.string.queue_remove_short_button
                            QueueRemoveMode.SWIPE -> R.string.queue_remove_short_swipe
                        },
                    ),
                    when (it) {
                        QueueRemoveMode.MENU -> AppSymbols.MoreVert
                        QueueRemoveMode.BUTTON -> AppSymbols.Close
                        QueueRemoveMode.SWIPE -> AppSymbols.SwipeLeft
                    },
                )
            },
            selected = settings.queueRemoveMode,
            onSelect = viewModel::setQueueRemoveMode,
        )

        SectionHeader(stringResource(R.string.settings_playback))
        SegmentedGroup {
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
        }

        SectionHeader(stringResource(R.string.lyrics_title))
        SegmentedGroup {
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
        }
    }
}

/** Whether history is recorded, how sessions count, and the statistics screen's defaults. */
@Composable
fun StatisticsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showDisableDialog by remember { mutableStateOf(false) }

    SettingsSubscreen(stringResource(R.string.settings_section_statistics), onBack) {
        SegmentedGroup(Modifier.padding(top = Spacing.s)) {
            SwitchRow(
                title = stringResource(R.string.settings_statistics_enabled),
                summary = stringResource(R.string.settings_statistics_enabled_summary),
                checked = settings.statisticsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) viewModel.setStatisticsEnabled(true) else showDisableDialog = true
                },
            )
        }
        val statsEnabled = settings.statisticsEnabled
        SectionHeader(stringResource(R.string.settings_section_recording))
        SegmentedGroup {
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
        }
        SectionHeader(stringResource(R.string.more_statistics))
        SegmentedGroup {
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

    if (showDisableDialog) {
        FormSheet(
            title = stringResource(R.string.settings_disable_stats_title),
            symbol = AppSymbols.History,
            supportingText = stringResource(R.string.settings_disable_stats_message),
            onDismiss = { showDisableDialog = false },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.settings_disable_stats_confirm),
            destructive = true,
            onConfirm = {
                viewModel.setStatisticsEnabled(false)
                showDisableDialog = false
            },
        )
    }
}

/** A tile whose corners follow a roundness setting, springing as the slider moves. */
@Composable
private fun CornerPreview(cornerDp: Int) {
    val corner by animateDpAsState(
        targetValue = (cornerDp * 0.5f).dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cornerPreview",
    )
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppSymbols.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, filled = true)
    }
}

/** A tiny grid showing how many album columns the library uses. */
@Composable
private fun ColumnsPreview(columns: Int) {
    Box(
        Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val cell = MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(32.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (columns - 1)) / columns
            for (row in 0 until columns) {
                for (col in 0 until columns) {
                    drawRoundRect(
                        color = cell,
                        topLeft = androidx.compose.ui.geometry.Offset(col * (w + gap), row * (w + gap)),
                        size = androidx.compose.ui.geometry.Size(w, w),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 4),
                    )
                }
            }
        }
    }
}

/**
 * A color swatch in the style of Android 16's theme picker: the top half shows the theme's
 * primary color, the bottom quadrants its secondary and tertiary containers. Selecting one morphs
 * it from a circle into a cookie and gives it a little spin.
 */
@Composable
private fun ThemeColorSwatch(
    theme: AppColorTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = colorSchemeFor(colorTheme = theme, darkTheme = LocalDarkTheme.current)
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchMorph",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .semantics { selected = isSelected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .pressScale(interactionSource, pressedScale = 0.9f)
                .graphicsLayer { rotationZ = progress * 40f }
                .clip(MorphShape(morph, progress)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(scheme.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(scheme.secondaryContainer, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(scheme.tertiaryContainer, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            AppIcon(
                AppSymbols.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                filled = true,
                modifier = Modifier
                    .graphicsLayer {
                        // Counter-rotate so the check stays upright while the cookie spins.
                        rotationZ = -progress * 40f
                        scaleX = progress
                        scaleY = progress
                        alpha = progress.coerceIn(0f, 1f)
                    }
                    .size(28.dp),
            )
        }
        Text(
            text = stringResource(colorThemeNameRes(theme)),
            style = if (isSelected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
