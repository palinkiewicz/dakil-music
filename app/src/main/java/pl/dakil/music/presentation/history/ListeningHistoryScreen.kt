package pl.dakil.music.presentation.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pl.dakil.music.R
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.songlist.DecomposeTitleDialog
import pl.dakil.music.presentation.songlist.EditTagsDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListeningHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListeningHistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current

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
                is HistoryEvent.ImportResult -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.history_import_result, event.imported, event.skipped),
                )
                is HistoryEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    Scaffold(
        modifier = modifier,
        // The host already insets for the bottom navigation bar; don't add it twice.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.inSelectionMode) {
                HistorySelectionBar(
                    selectedCount = state.selectedIds.size,
                    allSelectedFavorite = state.allSelectedAreFavorite,
                    onClose = viewModel::clearSelection,
                    onToggleFavorites = viewModel::toggleFavoritesForSelection,
                    onAddToQueue = viewModel::addSelectionToQueue,
                    onAddToPlaylist = viewModel::startAddToPlaylist,
                    onEditTags = viewModel::startEditTags,
                    onDecompose = viewModel::startDecompose,
                    onSelectAll = viewModel::selectAll,
                )
            } else {
                HistoryTopBar(
                    onBack = onBack,
                    onExport = {
                        exportLauncher.launch("listening-history.csv")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                    },
                )
            }
        },
    ) { padding ->
        if (state.records.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val groups = remember(state.records) { groupByDay(state.records) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { (label, records) ->
                    item(key = "h-$label") { DayHeader(label) }
                    items(records, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            state = state,
                            onClick = { viewModel.onRecordClick(record) },
                            onLongClick = { viewModel.onRecordLongClick(record) },
                        )
                    }
                }
                if (state.hasMore) {
                    item(key = "show-more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = viewModel::loadMore) {
                                Text(stringResource(R.string.history_show_more))
                            }
                        }
                    }
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.more_listening_history)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_export)) },
                        onClick = { menu = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_import)) },
                        onClick = { menu = false; onImport() },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySelectionBar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorites: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onDecompose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close_selection))
            }
        },
        actions = {
            IconButton(onClick = onToggleFavorites) {
                Icon(
                    imageVector = if (allSelectedFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (allSelectedFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites,
                    ),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Rounded.QueueMusic, stringResource(R.string.action_add_to_queue))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                        onClick = { menu = false; onAddToPlaylist() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit_tags)) },
                        onClick = { menu = false; onEditTags() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_decompose_title)) },
                        onClick = { menu = false; onDecompose() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        onClick = { menu = false; onSelectAll() },
                    )
                }
            }
        },
    )
}

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    record: ListeningRecord,
    state: HistoryUiState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val present = state.isPresent(record.songId)
    val selected = present && state.isSelected(record.songId)
    val current = present && state.isCurrent(record.songId)
    val favorite = present && state.isFavorite(record.songId)

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            .compositeOver(MaterialTheme.colorScheme.surface)
        else -> MaterialTheme.colorScheme.surface
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            when {
                state.inSelectionMode && present -> Checkbox(checked = selected, onCheckedChange = { onClick() })
                current -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                }
                !present -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> AlbumArt(
                    model = state.songsById[record.songId]?.coverArtModel() ?: record.albumArtUri,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp),
                )
            }
        },
        headlineContent = {
            Text(
                text = record.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        supportingContent = {
            Text(
                text = record.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.unknown_artist),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (favorite) {
                    Icon(
                        Icons.Rounded.Favorite,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.history_played_for, formatDuration(record.secondsPlayed * 1000L)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (record.timesPlayed > 1) {
                        Text(
                            stringResource(R.string.history_times_played, record.timesPlayed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline)
}

/** Orders records into per-day buckets, preserving the (newest-first) input order. */
private fun groupByDay(records: List<ListeningRecord>): List<Pair<String, List<ListeningRecord>>> {
    if (records.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    val result = ArrayList<Pair<String, MutableList<ListeningRecord>>>()
    var currentKey: String? = null
    for (record in records) {
        val date = Instant.ofEpochMilli(record.startTimestamp).atZone(zone).toLocalDate()
        val label = date.format(formatter)
        if (label != currentKey) {
            result.add(label to mutableListOf(record))
            currentKey = label
        } else {
            result.last().second.add(record)
        }
    }
    return result.map { it.first to it.second.toList() }
}
