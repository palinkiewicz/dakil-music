package pl.dakil.music.presentation.songlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.library.systemPlaylistNameRes
import pl.dakil.music.presentation.navigation.SongListSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SongListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editingSong by viewModel.editingSong.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SongListEvent.Message ->
                    snackbarHostState.showSnackbar(context.getString(event.res))

                is SongListEvent.RequestWritePermission ->
                    permissionLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build(),
                    )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onAddFavorites = viewModel::addSelectedToFavorites,
                    onEditTags = viewModel::startEditTags,
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(screenTitle(viewModel.source, state.songs)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (state.songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_empty_songs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index + 1,
                        selectionMode = state.inSelectionMode,
                        selected = state.isSelected(song.id),
                        favorite = state.isFavorite(song.id),
                        onClick = { viewModel.onSongClick(index) },
                        onLongClick = { viewModel.onSongLongClick(song.id) },
                    )
                }
            }
        }
    }

    editingSong?.let { song ->
        EditTagsDialog(
            song = song,
            onDismiss = viewModel::dismissEditTags,
            onSave = { edit -> viewModel.saveTags(song, edit) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddFavorites: () -> Unit,
    onEditTags: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount))
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close_selection))
            }
        },
        actions = {
            IconButton(onClick = onAddFavorites) {
                Icon(Icons.Rounded.Favorite, stringResource(R.string.action_add_to_favorites))
            }
            IconButton(onClick = onEditTags, enabled = selectedCount == 1) {
                Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit_tags))
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    index: Int,
    selectionMode: Boolean,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                AlbumArt(
                    uri = song.albumArtUri,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        },
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = song.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.unknown_artist),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (favorite) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun screenTitle(source: SongListSource, songs: List<Song>): String = when (source) {
    is SongListSource.AlbumSource ->
        songs.firstOrNull()?.album?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.unknown_album)

    is SongListSource.PerformerSource -> source.name
    is SongListSource.PlaylistSource -> stringResource(systemPlaylistNameRes(source.playlist))
}
