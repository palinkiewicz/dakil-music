package pl.dakil.music.presentation.statistics

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.StatRangeType
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.ArtistAvatar
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.statMetricNameRes
import pl.dakil.music.presentation.components.statRangeTypeNameRes
import pl.dakil.music.presentation.components.weekdayShortNameRes
import pl.dakil.music.presentation.history.MergeWithExistingDialog
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuChoiceItem
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.ConnectedToggleRow
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.PageColumn
import pl.dakil.music.ui.designsystem.RollingNumber
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.ToggleOption
import pl.dakil.music.ui.designsystem.entrance
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many rows a ranking shows before "show more". */
private const val TOP_ROWS = 5

/**
 * Listening statistics in the Dakilio Insights style: range and period up top, a hero card with
 * the total time, three count tiles, rankings with proportional fills, and the daily/weekly
 * routine charts.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val liveSongs by viewModel.liveSongs.collectAsStateWithLifecycle()
    val mergeTarget by viewModel.mergeTarget.collectAsStateWithLifecycle()
    val liveIds = remember(liveSongs) { liveSongs.mapTo(HashSet()) { it.id } }
    val liveById = remember(liveSongs) { liveSongs.associateBy { it.id } }

    CollapsingScaffold(
        title = stringResource(R.string.more_statistics),
        navigationIcon = { BackButton(onBack) },
        modifier = modifier,
    ) { padding ->
        when {
            state.loading && state.empty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            state.empty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(AppSymbols.Insights, stringResource(R.string.history_empty), polygon = MaterialShapes.Sunny)
            }
            else -> PageColumn(padding, spacing = Spacing.m) {
                val stats = state.statistics
                val total = stats.totalValue(state.metric)
                RangeControls(state, viewModel)
                TotalTimeCard(stats.totalSeconds, Modifier.entrance(0))
                CountTiles(stats, Modifier.entrance(1))
                ConnectedToggleRow(
                    options = listOf(
                        ToggleOption(StatMetric.SECONDS, stringResource(statMetricNameRes(StatMetric.SECONDS)), AppSymbols.Schedule),
                        ToggleOption(StatMetric.PLAYS, stringResource(statMetricNameRes(StatMetric.PLAYS)), AppSymbols.PlayCircle),
                    ),
                    selected = state.metric,
                    onSelect = viewModel::setMetric,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                RankingCard(
                    title = stringResource(R.string.stats_top_tracks),
                    symbol = AppSymbols.MusicNote,
                    items = stats.topTracks,
                    value = { it.value(state.metric) },
                    metric = state.metric,
                    total = total,
                ) { track ->
                    val deleted = track.songId !in liveIds
                    RankLeadingArt(
                        model = liveById[track.songId]?.coverArtModel() ?: track.albumArtUri,
                        deleted = deleted,
                    )
                    RankTitles(
                        title = track.title,
                        subtitle = track.artists.joinToString(", ").ifBlank { stringResource(R.string.unknown_artist) },
                        onLongClick = if (deleted) ({ viewModel.startMerge(track) }) else null,
                    )
                }
                RankingCard(
                    title = stringResource(R.string.stats_top_artists),
                    symbol = AppSymbols.Person,
                    items = stats.topArtists,
                    value = { it.value(state.metric) },
                    metric = state.metric,
                    total = total,
                ) { artist ->
                    ArtistAvatar(artist.name, size = 40.dp, textStyle = MaterialTheme.typography.labelLargeEmphasized)
                    RankTitles(title = artist.name, subtitle = null)
                }
                RankingCard(
                    title = stringResource(R.string.stats_top_albums),
                    symbol = AppSymbols.Album,
                    items = stats.topAlbums,
                    value = { it.value(state.metric) },
                    metric = state.metric,
                    total = total,
                ) { album ->
                    RankLeadingArt(model = album.albumArtUri, deleted = false)
                    RankTitles(title = album.album.ifBlank { stringResource(R.string.no_album) }, subtitle = null)
                }
                RoutinesCard(stats, state.metric, state.firstDayOfWeek)
            }
        }
    }

    val target = mergeTarget
    if (target != null) {
        MergeWithExistingDialog(
            label = target.title,
            songs = liveSongs,
            onDismiss = viewModel::dismissMerge,
            onMerge = { song -> viewModel.mergeInto(target.songId, song) },
        )
    }
}

private fun Statistics.totalValue(metric: StatMetric): Long =
    if (metric == StatMetric.SECONDS) totalSeconds else totalPlays

/** The range type as a connected row, then the period as a pill flanked by previous/next. */
@Composable
private fun RangeControls(state: StatisticsUiState, viewModel: StatisticsViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    ConnectedToggleRow(
        options = StatRangeType.entries.map { ToggleOption(it, stringResource(statRangeTypeNameRes(it))) },
        selected = state.rangeType,
        onSelect = viewModel::setRangeType,
    )
    when (state.rangeType) {
        StatRangeType.ALL_TIME -> Unit
        StatRangeType.YEARLY -> PeriodStepper(
            options = state.years,
            selected = state.selectedYear,
            label = { it.toString() },
            onSelect = viewModel::selectYear,
        )
        StatRangeType.MONTHLY -> PeriodStepper(
            options = state.months,
            selected = state.selectedMonth,
            label = { it.formatMonth(locale) },
            onSelect = viewModel::selectMonth,
        )
        StatRangeType.WEEKLY -> PeriodStepper(
            options = state.weeks,
            selected = state.selectedWeek,
            label = { it.formatWeek(locale) },
            onSelect = viewModel::selectWeek,
        )
    }
}

/**
 * The selected period between previous/next buttons; the pill itself opens the full list. The
 * option lists are newest-first, so "previous" (older) is the next index.
 */
@Composable
private fun <T> PeriodStepper(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberHaptics()
    val index = options.indexOf(selected)
    var expanded by remember { mutableStateOf(false) }
    fun step(to: Int) {
        options.getOrNull(to)?.let {
            haptics.tick()
            onSelect(it)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilledTonalIconButton(
            onClick = { step(index + 1) },
            enabled = index in 0 until options.lastIndex,
            shapes = IconButtonDefaults.shapes(),
        ) { AppIcon(AppSymbols.ChevronLeft, contentDescription = null) }
        Box(Modifier.weight(1f)) {
            FilledTonalButton(
                onClick = { expanded = true },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIcon(AppSymbols.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppMenuGroup {
                    options.forEach { option ->
                        AppMenuChoiceItem(
                            text = label(option),
                            selected = option == selected,
                            onClick = {
                                expanded = false
                                if (option != selected) haptics.tick()
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        }
        FilledTonalIconButton(
            onClick = { step(index - 1) },
            enabled = index > 0,
            shapes = IconButtonDefaults.shapes(),
        ) { AppIcon(AppSymbols.ChevronRight, contentDescription = null) }
    }
}

/** The headline: total listening time, rolling to each new value, beside a slowly turning badge. */
@Composable
private fun TotalTimeCard(totalSeconds: Long, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ShapedIcon(
                symbol = AppSymbols.Schedule,
                polygon = MaterialShapes.Cookie12Sided,
                size = 88.dp,
                iconSize = 40.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Column {
                Text(stringResource(R.string.stats_total_time), style = MaterialTheme.typography.titleSmall)
                RollingNumber(
                    formatListeningTime(totalSeconds),
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                )
            }
        }
    }
}

/** Songs · artists · plays as three equal tiles with rolling numbers. */
@Composable
private fun CountTiles(stats: Statistics, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        CountTile(stats.distinctSongs.toLong(), stringResource(R.string.stats_tile_songs), Modifier.weight(1f))
        CountTile(stats.distinctArtists.toLong(), stringResource(R.string.stats_tile_artists), Modifier.weight(1f))
        CountTile(stats.totalPlays, stringResource(R.string.stats_tile_plays), Modifier.weight(1f))
    }
}

@Composable
private fun CountTile(value: Long, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            RollingNumber(value.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A ranking (top tracks / artists / albums). Each row carries a tonal fill proportional to its
 * value against the leader, so the shape of the ranking reads at a glance.
 */
@Composable
private fun <T> RankingCard(
    title: String,
    symbol: AppSymbol,
    items: List<T>,
    value: (T) -> Long,
    metric: StatMetric,
    total: Long,
    row: @Composable androidx.compose.foundation.layout.RowScope.(T) -> Unit,
) {
    if (items.isEmpty()) return
    var expanded by remember(items) { mutableStateOf(false) }
    val visible = if (expanded) items else items.take(TOP_ROWS)
    val leader = value(items.first()).coerceAtLeast(1L)
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "rankChevron",
    )
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
            ) {
                ShapedIcon(symbol, size = 32.dp)
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            }
            visible.forEachIndexed { index, item ->
                val v = value(item)
                val fraction = (v.toFloat() / leader).coerceIn(0f, 1f)
                val pct = if (total > 0) v * 100.0 / total else 0.0
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large),
                ) {
                    // The proportional fill sits behind the row's content.
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.titleMediumEmphasized.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 20.dp),
                        )
                        row(item)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMetricValue(v, metric), style = MaterialTheme.typography.labelLarge.tabular())
                            Text(
                                String.format(locale, "%.1f%%", pct),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (items.size > TOP_ROWS) {
                TextButton(onClick = { expanded = !expanded }, shapes = ButtonDefaults.shapes()) {
                    AppIcon(
                        AppSymbols.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronTurn },
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(if (expanded) R.string.stats_show_less else R.string.stats_show_more))
                }
            }
        }
    }
}

@Composable
private fun RankLeadingArt(model: Any?, deleted: Boolean) {
    if (deleted) {
        ShapedIcon(
            symbol = AppSymbols.MusicOff,
            polygon = MaterialShapes.Cookie6Sided,
            size = 40.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        AlbumArt(model = model, shape = MaterialTheme.shapes.small, modifier = Modifier.size(40.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.RowScope.RankTitles(
    title: String,
    subtitle: String?,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .weight(1f)
            .then(if (onLongClick != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick) else Modifier),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Peak hours and weekdays as two bar charts in one card. */
@Composable
private fun RoutinesCard(stats: Statistics, metric: StatMetric, firstDayOfWeek: DayOfWeek) {
    val weekdayLabels = (0 until 7).map { i ->
        val iso = ((firstDayOfWeek.value - 1 + i) % 7) + 1
        stringResource(weekdayShortNameRes(iso))
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ShapedIcon(AppSymbols.Insights, size = 32.dp)
                Text(stringResource(R.string.stats_routines), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            BarChart(
                title = stringResource(R.string.stats_peak_hours),
                values = stats.hourHistogram,
                metric = metric,
                barLabel = { i -> "%02d:00".format(i) },
                axisLabel = { i -> if (i % 6 == 0) i.toString() else null },
            )
            BarChart(
                title = stringResource(R.string.stats_peak_weekdays),
                values = stats.weekdayHistogram,
                metric = metric,
                barLabel = { i -> weekdayLabels[i] },
                axisLabel = { i -> weekdayLabels[i] },
            )
        }
    }
}

/**
 * A single-series bar chart: one hue, thin bars with rounded tops anchored to the baseline and a
 * small gap between them; the busiest bar is drawn at full strength. Bars grow in on a spring
 * whenever the data changes. Touch (or drag across) to read a bar; the readout above the chart
 * defaults to the peak. The values are also exposed to accessibility as a description.
 */
@Composable
private fun BarChart(
    title: String,
    values: LongArray,
    metric: StatMetric,
    barLabel: (Int) -> String,
    axisLabel: (Int) -> String?,
) {
    val haptics = rememberHaptics()
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val peak = values.indices.maxByOrNull { values[it] } ?: 0
    var selected by remember(values.toList()) { mutableIntStateOf(-1) }
    val grow = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(values.toList()) {
        grow.snapTo(0f)
        grow.animateTo(1f, spec)
    }

    val shown = if (selected >= 0) selected else peak
    val readout = "${barLabel(shown)}  ·  ${formatMetricValue(values.getOrElse(shown) { 0L }, metric)}"
    val description = values.indices.joinToString { "${barLabel(it)}: ${values[it]}" }

    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(readout, style = MaterialTheme.typography.labelLargeEmphasized.tabular())
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .semantics { contentDescription = "$title. $description" }
                .pointerInput(values.size) {
                    fun indexAt(x: Float) = (x / size.width * values.size).toInt().coerceIn(0, values.lastIndex)
                    detectTapGestures(
                        onPress = { offset ->
                            val i = indexAt(offset.x)
                            if (i != selected) haptics.tick()
                            selected = i
                            tryAwaitRelease()
                            selected = -1
                        },
                    )
                }
                .pointerInput(values.size) {
                    fun indexAt(x: Float) = (x / size.width * values.size).toInt().coerceIn(0, values.lastIndex)
                    detectDragGestures(
                        onDragEnd = { selected = -1 },
                        onDragCancel = { selected = -1 },
                    ) { change, _ ->
                        val i = indexAt(change.position.x)
                        if (i != selected) haptics.frequentTick()
                        selected = i
                    }
                },
        ) {
            val gap = 2.dp.toPx()
            val barWidth = (size.width - gap * (values.size - 1)) / values.size
            val radius = (4.dp.toPx()).coerceAtMost(barWidth / 2)
            values.forEachIndexed { i, v ->
                val left = i * (barWidth + gap)
                // A faint full-height track keeps empty slots readable as "zero", not missing.
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(radius))
                val h = size.height * (v.toFloat() / max) * grow.value
                if (h > 0f) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = size.height - h,
                                right = left + barWidth,
                                bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius),
                                topRightCornerRadius = CornerRadius(radius),
                                bottomLeftCornerRadius = CornerRadius(radius),
                                bottomRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, if (i == shown) strong else soft)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            values.indices.forEach { i ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    axisLabel(i)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// --- Formatting helpers ----------------------------------------------------------

private fun YearMonth.formatMonth(locale: Locale): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

private fun LocalDate.formatWeek(locale: Locale): String {
    val end = plusDays(6)
    val start = format(DateTimeFormatter.ofPattern("d MMM", locale))
    val endStr = end.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    return "$start – $endStr"
}

private fun formatListeningTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${totalSeconds % 60}s"
}

@Composable
private fun formatMetricValue(value: Long, metric: StatMetric): String =
    if (metric == StatMetric.SECONDS) {
        formatDuration(value * 1000L)
    } else {
        pluralStringResource(R.plurals.stats_play_count, value.toInt(), value.toInt())
    }
