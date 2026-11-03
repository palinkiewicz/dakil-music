package pl.dakil.music.presentation.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.dakil.music.R
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.LocalIsPlaying
import pl.dakil.music.presentation.components.NowPlayingBars
import pl.dakil.music.presentation.components.SelectableArt
import pl.dakil.music.presentation.components.SelectionChrome
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.shareSongs
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.songlist.DecomposeTitleDialog
import pl.dakil.music.presentation.songlist.EditTagsDialog
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The listening history as a timeline: a big day number heads each day (pinned while its plays
 * scroll by), the day's plays form one segmented card. More history loads as you reach the end.
 */
@Composable
fun ListeningHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Hidden (with a large title) when the screen is a bottom-bar tab: a tab has nothing to go back to.
    showBack: Boolean = true,
    viewModel: ListeningHistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberHaptics()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onWritePermissionGranted()
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) viewModel.exportHistory(context.contentResolver, uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importHistory(context.contentResolver, uri) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(resources.getString(event.res))
                is HistoryEvent.ImportResult -> {
                    haptics.confirm()
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.history_import_result, event.imported, event.skipped),
                    )
                }
                is HistoryEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    // Back leaves the selection mode first instead of leaving the screen.
    BackHandler(enabled = state.inSelectionMode) {
        viewModel.clearSelection()
    }

    Box(modifier.fillMaxSize()) {
        CollapsingScaffold(
            title = stringResource(R.string.more_listening_history),
            large = !showBack,
            navigationIcon = { if (showBack) BackButton(onBack) },
            actions = {
                HistoryMenu(
                    onExport = { exportLauncher.launch("listening-history.csv") },
                    onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when {
                state.records.isEmpty() && state.loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                state.records.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(AppSymbols.History, stringResource(R.string.history_empty), polygon = MaterialShapes.Cookie7Sided)
                }

                else -> HistoryList(
                    state = state,
                    onRecordClick = viewModel::onRecordClick,
                    onRecordLongClick = viewModel::onRecordLongClick,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        SelectionChrome(
            visible = state.inSelectionMode,
            selectedCount = state.selectedIds.size,
            allSelectedFavorite = state.allSelectedAreFavorite,
            singleSelectionHasArt = false,
            showRemoveFromPlaylist = false,
            onClose = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onToggleFavorites = viewModel::toggleFavoritesForSelection,
            onAddToQueue = viewModel::addSelectionToQueue,
            onAddToPlaylist = viewModel::startAddToPlaylist,
            onEditTags = viewModel::startEditTags,
            onDecompose = viewModel::startDecompose,
            onChangeCoverArt = null,
            onRemoveFromPlaylist = {},
            onShare = {
                shareSongs(context, state.selectedIds.mapNotNull { state.songsById[it] })
                viewModel.clearSelection()
            },
            onShowInfo = null,
        )
    }

    when (val current = dialog) {
        is HistoryDialog.EditTags -> EditTagsDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onSave = { edit -> viewModel.saveTags(current.songs, edit) },
        )
        is HistoryDialog.Decompose -> DecomposeTitleDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onApply = { options -> viewModel.applyDecomposition(current.songs, options) },
        )
        is HistoryDialog.AddToPlaylist -> AddToPlaylistDialog(
            playlists = userPlaylists,
            onDismiss = viewModel::dismissDialog,
            onSelect = { id -> viewModel.addToExistingPlaylist(id, current.songs) },
            onCreateNew = { name -> viewModel.createPlaylistAndAdd(name, current.songs) },
        )
        is HistoryDialog.Merge -> MergeWithExistingDialog(
            label = current.label,
            songs = state.songsById.values.toList(),
            onDismiss = viewModel::dismissDialog,
            onMerge = { target -> viewModel.mergeDeletedSong(current.deletedSongId, target) },
        )
        null -> Unit
    }
}

@Composable
private fun HistoryMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
            AppIcon(AppSymbols.MoreVert, stringResource(R.string.action_more))
        }
        AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AppMenuGroup {
                AppMenuItem(
                    text = stringResource(R.string.history_export),
                    symbol = AppSymbols.Upload,
                    onClick = { menu = false; onExport() },
                )
                AppMenuItem(
                    text = stringResource(R.string.history_import),
                    symbol = AppSymbols.Download,
                    onClick = { menu = false; onImport() },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(
    state: HistoryUiState,
    onRecordClick: (ListeningRecord) -> Unit,
    onRecordLongClick: (ListeningRecord) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(state.records) { groupByDay(state.records) }
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val hasMore by rememberUpdatedState(state.hasMore)

    // Fetch the next page once the end of the list comes into view.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd && hasMore) loadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
    ) {
        groups.forEach { (date, records) ->
            stickyHeader(key = "h-$date") { DayHeader(date) }
            itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                HistoryRow(
                    record = record,
                    state = state,
                    shape = segmentedItemShape(index, records.size),
                    onClick = { onRecordClick(record) },
                    onLongClick = { onRecordLongClick(record) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.hasMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

/**
 * A day's header in the Dakilio timeline style: the day number big and rounded, beside it
 * "Today"/"Yesterday" or the weekday, and the month and year.
 */
@Composable
private fun DayHeader(date: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val dayLabel = when (date) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val monthLabel = date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = Spacing.xs, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.displaySmallEmphasized.tabular(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(Modifier.padding(start = Spacing.s)) {
            Text(dayLabel, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    record: ListeningRecord,
    state: HistoryUiState,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val present = state.isPresent(record.songId)
    val selected = present && state.isSelected(record.songId)
    val current = present && state.isCurrent(record.songId)
    val favorite = present && state.isFavorite(record.songId)

    val container by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "historyRowColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .combinedClickable(
                onClick = {
                    if (state.inSelectionMode) haptics.tick()
                    onClick()
                },
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            )
            .padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!present) {
                // The song is gone from the device; long-press offers to merge its history.
                ShapedIcon(
                    symbol = AppSymbols.MusicOff,
                    polygon = MaterialShapes.Cookie6Sided,
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectableArt(
                    model = state.songsById[record.songId]?.coverArtModel() ?: record.albumArtUri,
                    selected = selected,
                )
                if (current && !selected) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingBars(isPlaying = LocalIsPlaying.current, color = Color.White)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    current -> MaterialTheme.colorScheme.primary
                    !present -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            AppIcon(
                AppSymbols.Favorite,
                contentDescription = null,
                filled = true,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.history_played_for, formatDuration(record.secondsPlayed * 1000L)),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.timesPlayed > 1) {
                Text(
                    stringResource(R.string.history_times_played, record.timesPlayed),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Orders records into per-day buckets, preserving the (newest-first) input order. */
private fun groupByDay(records: List<ListeningRecord>): List<Pair<LocalDate, List<ListeningRecord>>> {
    if (records.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val result = ArrayList<Pair<LocalDate, MutableList<ListeningRecord>>>()
    for (record in records) {
        val date = Instant.ofEpochMilli(record.startTimestamp).atZone(zone).toLocalDate()
        if (result.lastOrNull()?.first != date) {
            result.add(date to mutableListOf(record))
        } else {
            result.last().second.add(record)
        }
    }
    return result.map { it.first to it.second.toList() }
}
