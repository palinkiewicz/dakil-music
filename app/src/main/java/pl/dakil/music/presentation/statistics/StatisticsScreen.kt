package pl.dakil.music.presentation.statistics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.domain.model.AlbumStat
import pl.dakil.music.domain.model.ArtistStat
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.StatRangeType
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.TrackStat
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.statMetricNameRes
import pl.dakil.music.presentation.components.statRangeTypeNameRes
import pl.dakil.music.presentation.components.weekdayShortNameRes
import pl.dakil.music.presentation.history.MergeWithExistingDialog
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.empty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Selectors(state, viewModel)
            Overview(state.statistics, state.metric)
            MetricToggle(state.metric, viewModel::setMetric)

            val stats = state.statistics
            TopTracksSection(
                tracks = stats.topTracks,
                metric = state.metric,
                total = stats.totalValue(state.metric),
                isDeleted = { it.songId !in liveIds },
                onMergeDeleted = viewModel::startMerge,
            )
            TopArtistsSection(stats.topArtists, state.metric, stats.totalValue(state.metric))
            TopAlbumsSection(stats.topAlbums, state.metric, stats.totalValue(state.metric))

            RoutinesSection(stats, state.metric, state.firstDayOfWeek)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Selectors(state: StatisticsUiState, viewModel: StatisticsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownSelector(
            label = stringResource(statRangeTypeNameRes(state.rangeType)),
            options = StatRangeType.entries.map { it to stringResource(statRangeTypeNameRes(it)) },
            onSelect = viewModel::setRangeType,
            modifier = Modifier.weight(1f),
        )
        when (state.rangeType) {
            StatRangeType.ALL_TIME -> Spacer(Modifier.weight(1f))
            StatRangeType.YEARLY -> DropdownSelector(
                label = state.selectedYear.toString(),
                options = state.years.map { it to it.toString() },
                onSelect = viewModel::selectYear,
                modifier = Modifier.weight(1f),
            )
            StatRangeType.MONTHLY -> DropdownSelector(
                label = state.selectedMonth.formatMonth(),
                options = state.months.map { it to it.formatMonth() },
                onSelect = viewModel::selectMonth,
                modifier = Modifier.weight(1f),
            )
            StatRangeType.WEEKLY -> DropdownSelector(
                label = state.selectedWeek.formatWeek(),
                options = state.weeks.map { it to it.formatWeek() },
                onSelect = viewModel::selectWeek,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun Overview(stats: Statistics, metric: StatMetric) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.stats_total_time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatListeningTime(stats.totalSeconds), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.stats_overview_counts,
                    stats.distinctSongs,
                    stats.distinctArtists,
                    stats.totalPlays,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricToggle(metric: StatMetric, onSelect: (StatMetric) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatMetric.entries.forEach { option ->
            val selected = option == metric
            AssistChip(
                onClick = { onSelect(option) },
                label = { Text(stringResource(statMetricNameRes(option))) },
                colors = if (selected) {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun TopTracksSection(
    tracks: List<TrackStat>,
    metric: StatMetric,
    total: Long,
    isDeleted: (TrackStat) -> Boolean,
    onMergeDeleted: (TrackStat) -> Unit,
) {
    ChartSection(stringResource(R.string.stats_top_tracks), tracks) { track ->
        val deleted = isDeleted(track)
        ChartRow(
            artUri = track.albumArtUri,
            deleted = deleted,
            title = track.title,
            subtitle = track.artists.joinToString(", ").ifBlank { stringResource(R.string.unknown_artist) },
            value = track.value(metric),
            metric = metric,
            total = total,
            onLongClick = if (deleted) ({ onMergeDeleted(track) }) else null,
        )
    }
}

@Composable
private fun TopArtistsSection(artists: List<ArtistStat>, metric: StatMetric, total: Long) {
    ChartSection(stringResource(R.string.stats_top_artists), artists) { artist ->
        ChartRow(
            artUri = null,
            deleted = false,
            title = artist.name,
            subtitle = null,
            value = artist.value(metric),
            metric = metric,
            total = total,
        )
    }
}

@Composable
private fun TopAlbumsSection(albums: List<AlbumStat>, metric: StatMetric, total: Long) {
    ChartSection(stringResource(R.string.stats_top_albums), albums) { album ->
        ChartRow(
            artUri = album.albumArtUri,
            deleted = false,
            title = album.album.ifBlank { stringResource(R.string.no_album) },
            subtitle = null,
            value = album.value(metric),
            metric = metric,
            total = total,
        )
    }
}

@Composable
private fun <T> ChartSection(title: String, items: List<T>, row: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    var expanded by remember(items) { mutableStateOf(false) }
    val visible = if (expanded) items else items.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        visible.forEach { row(it) }
        if (items.size > 5) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(if (expanded) R.string.stats_show_less else R.string.stats_show_more),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartRow(
    artUri: android.net.Uri?,
    deleted: Boolean,
    title: String,
    subtitle: String?,
    value: Long,
    metric: StatMetric,
    total: Long,
    onLongClick: (() -> Unit)? = null,
) {
    val pct = if (total > 0) value * 100.0 / total else 0.0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onLongClick != null) {
                    base.combinedClickable(onClick = {}, onLongClick = onLongClick)
                } else {
                    base
                }
            }
            .padding(vertical = 4.dp),
    ) {
        if (deleted) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AlbumArt(uri = artUri, shape = MaterialTheme.shapes.small, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
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
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMetricValue(value, metric), style = MaterialTheme.typography.labelLarge)
            Text(
                String.format(Locale.getDefault(), "%.1f%%", pct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoutinesSection(stats: Statistics, metric: StatMetric, firstDayOfWeek: DayOfWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stats_routines), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.stats_peak_hours),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BarChart(values = stats.hourHistogram, labelFor = { i -> if (i % 6 == 0) i.toString() else "" })
        Text(
            stringResource(R.string.stats_peak_weekdays),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BarChart(
            values = stats.weekdayHistogram,
            labelFor = { i ->
                val iso = ((firstDayOfWeek.value - 1 + i) % 7) + 1
                stringResource(weekdayShortNameRes(iso))
            },
        )
    }
}

@Composable
private fun BarChart(values: LongArray, labelFor: @Composable (Int) -> String) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((96.dp.value * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    labelFor(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Formatting helpers ----------------------------------------------------------

private fun YearMonth.formatMonth(): String =
    format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

private fun java.time.LocalDate.formatWeek(): String {
    val end = plusDays(6)
    val start = format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    val endStr = end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    return "$start – $endStr"
}

private fun formatListeningTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "${h}h ${m}m ${s}s"
}

@Composable
private fun formatMetricValue(value: Long, metric: StatMetric): String =
    if (metric == StatMetric.SECONDS) {
        formatDuration(value * 1000L)
    } else {
        pluralStringResource(R.plurals.stats_play_count, value.toInt(), value.toInt())
    }
