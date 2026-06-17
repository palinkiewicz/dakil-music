package pl.dakil.music.presentation.songlist

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pl.dakil.music.R
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.SongPickerDialog
import pl.dakil.music.presentation.components.clickableRow
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.toSongEntries
import pl.dakil.music.presentation.library.systemPlaylistNameRes
import pl.dakil.music.presentation.navigation.SongListSource
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.playlist.PlaylistNameDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SongListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val currentAlbum by viewModel.currentAlbum.collectAsStateWithLifecycle()
    val authoredAlbums by viewModel.authoredAlbums.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted()
        }
    }

    // Native png/jpeg picker for cover art. pickForAlbum routes the result.
    var pickForAlbum by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onCoverArtPicked(context.contentResolver, uri, pickForAlbum)
    }
    fun launchImagePicker(forAlbum: Boolean) {
        pickForAlbum = forAlbum
        imagePicker.launch(arrayOf("image/png", "image/jpeg"))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SongListEvent.Message ->
                    snackbarHostState.showSnackbar(context.getString(event.res))

                is SongListEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())

                is SongListEvent.NavigateBack -> onBack()
            }
        }
    }

    val source = viewModel.source
    val title = headerTitle(source, state.songs, userPlaylists)
    val isAlbum = source is SongListSource.AlbumSource
    val isPerformer = source is SongListSource.PerformerSource
    val isUserPlaylist = source is SongListSource.UserPlaylistSource
    val isFavorites = source is SongListSource.PlaylistSource &&
        source.playlist == pl.dakil.music.domain.model.SystemPlaylist.FAVORITES
    val canAddToPlaylist = isUserPlaylist || isFavorites
    val cornerDp = settings.albumCornerRoundnessDp.dp

    val listState = rememberLazyListState()
    // Once the header has scrolled away, pin the title in the top bar.
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 }
    }

    // Local working copy for playlist drag-reorder (mirrors the Now Playing queue).
    var localEntries by remember { mutableStateOf(state.songs.toSongEntries()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(state.songs) {
        if (!isDragging) localEntries = state.songs.toSongEntries()
    }
    // The header is the single item before the playlist songs.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - 1
        val t = to.index - 1
        if (f in localEntries.indices && t in localEntries.indices) {
            localEntries = localEntries.toMutableList().apply { add(t, removeAt(f)) }
        }
    }
    val reorderable = (isUserPlaylist || isFavorites) && !state.inSelectionMode

    Box(modifier = modifier.fillMaxSize()) {
        if (state.songs.isEmpty()) {
            EmptyMessage()
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 104.dp), // clear the FABs
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    SongListHeader(
                        title = title,
                        artUri = state.songs.firstOrNull()?.albumArtUri,
                        author = if (isAlbum) currentAlbum?.artist?.takeIf { it.isNotBlank() } else null,
                        year = if (isAlbum) currentAlbum?.year ?: 0 else 0,
                        songCount = state.songs.size,
                        totalDurationMs = state.songs.sumOf { it.durationMs },
                        cornerShape = RoundedCornerShape(cornerDp),
                    )
                }

                if (isPerformer && authoredAlbums.isNotEmpty()) {
                    item(key = "authored_albums") {
                        AuthoredAlbumsSection(
                            albums = authoredAlbums,
                            cornerShape = RoundedCornerShape(cornerDp),
                            onAlbumClick = onAlbumClick,
                        )
                    }
                    item(key = "songs_subheader") {
                        SectionSubheader(stringResource(R.string.songs_section))
                    }
                }

                if (reorderable) {
                    itemsIndexed(localEntries, key = { _, entry -> entry.key }) { index, entry ->
                        ReorderableItem(reorderState, key = entry.key) { _ ->
                            val itemScope = this
                            // The drag handle sits *outside* the row's clickable area so a
                            // long-press on it starts a drag (queue-style) without ever
                            // triggering the row's long-press selection.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SongRow(
                                        song = entry.song,
                                        position = index + 1,
                                        albumMode = false,
                                        selectionMode = false,
                                        selected = false,
                                        favorite = state.isFavorite(entry.song.id),
                                        current = state.isCurrent(entry.song.id),
                                        onClick = { viewModel.onSongClick(index) },
                                        onLongClick = { viewModel.onSongLongClick(entry.song.id) },
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = stringResource(R.string.cd_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = with(itemScope) {
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                isDragging = true
                                                dragStartIndex = index
                                            },
                                            onDragStopped = {
                                                isDragging = false
                                                val to = localEntries.indexOfFirst { it.key == entry.key }
                                                if (dragStartIndex in localEntries.indices &&
                                                    to >= 0 && dragStartIndex != to
                                                ) {
                                                    viewModel.movePlaylistSong(dragStartIndex, to)
                                                }
                                                dragStartIndex = -1
                                            },
                                        )
                                    }
                                        .padding(end = 12.dp)
                                        .size(24.dp),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            position = index + 1,
                            albumMode = isAlbum,
                            selectionMode = state.inSelectionMode,
                            selected = state.isSelected(song.id),
                            favorite = state.isFavorite(song.id),
                            current = state.isCurrent(song.id),
                            onClick = { viewModel.onSongClick(index) },
                            onLongClick = { viewModel.onSongLongClick(song.id) },
                        )
                    }
                }
            }
        }

        // Top bar overlay: selection actions, or a back/title bar that fades in on scroll.
        if (state.inSelectionMode) {
            SelectionTopBar(
                selectedCount = state.selectedIds.size,
                allSelectedFavorite = state.allSelectedAreFavorite,
                singleSelectionHasArt = state.selectedIds.size == 1 &&
                    state.songs.firstOrNull { it.id in state.selectedIds }?.albumArtUri != null,
                showRemoveFromPlaylist = isUserPlaylist,
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onToggleFavorites = viewModel::toggleFavoritesForSelection,
                onAddToQueue = viewModel::addSelectionToQueue,
                onAddToPlaylist = viewModel::startAddToPlaylist,
                onEditTags = viewModel::startEditTags,
                onDecompose = viewModel::startDecompose,
                onChangeCoverArt = { launchImagePicker(forAlbum = false) },
                onRemoveFromPlaylist = viewModel::removeSelectionFromPlaylist,
                modifier = Modifier.align(Alignment.TopStart),
            )
        } else {
            CollapsingTopBar(
                title = title,
                collapsed = collapsed,
                onBack = onBack,
                onAddToQueue = viewModel::addAllToQueue,
                onAddToPlaylist = if (canAddToPlaylist) viewModel::startAddSongToPlaylist else null,
                onEdit = when {
                    isAlbum -> viewModel::openAlbumEdit
                    isPerformer -> viewModel::openArtistEdit
                    isUserPlaylist -> viewModel::openPlaylistEdit
                    else -> null
                },
                onChangeCoverArt = if (isAlbum) {
                    { launchImagePicker(forAlbum = true) }
                } else {
                    null
                },
                albumHasArt = currentAlbum?.artworkUri != null,
                onRemove = if (isUserPlaylist) viewModel::promptRemovePlaylist else null,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // Play-all / shuffle FABs, hidden while selecting.
        if (!state.inSelectionMode && state.songs.isNotEmpty()) {
            PlaybackFabs(
                onPlayAll = viewModel::playAll,
                onShuffle = viewModel::shufflePlay,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (val current = dialog) {
        is SongDialog.EditTags -> EditTagsDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onSave = { edit -> viewModel.saveTags(current.songs, edit) },
        )

        is SongDialog.Decompose -> DecomposeTitleDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onApply = { options -> viewModel.applyDecomposition(current.songs, options) },
        )

        is SongDialog.AddToPlaylist -> AddToPlaylistDialog(
            playlists = userPlaylists,
            onDismiss = viewModel::dismissDialog,
            onSelect = { id -> viewModel.addToExistingPlaylist(id, current.songs) },
            onCreateNew = { name -> viewModel.createPlaylistAndAdd(name, current.songs) },
        )

        is SongDialog.AddSongToPlaylist -> SongPickerDialog(
            title = stringResource(R.string.action_add_to_playlist),
            songs = allSongs,
            onDismiss = viewModel::dismissDialog,
            onPick = viewModel::addSongToCurrentPlaylist,
        )

        is SongDialog.EditAlbum -> EditAlbumDialog(
            dialog = current,
            onDismiss = viewModel::dismissDialog,
            onSave = { t, y, custom, cover, author -> viewModel.saveAlbumEdit(t, y, custom, cover, author) },
        )

        is SongDialog.EditArtist -> EditArtistDialog(
            initialName = current.name,
            onDismiss = viewModel::dismissDialog,
            onSave = viewModel::saveArtistName,
        )

        is SongDialog.EditPlaylist -> PlaylistNameDialog(
            titleRes = R.string.playlist_rename,
            confirmRes = R.string.playlist_rename_confirm,
            initialName = current.currentName,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::savePlaylistName,
        )

        is SongDialog.ConfirmRemovePlaylist -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message, current.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::removeCurrentPlaylist) {
                    Text(stringResource(R.string.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is SongDialog.ConfirmAlbumRetag -> ConfirmRetagDialog(
            count = current.count,
            onConfirm = { viewModel.confirmAlbumRetag(current.edit, current.songs) },
            onDismiss = viewModel::dismissDialog,
        )

        is SongDialog.ConfirmArtistRetag -> ConfirmRetagDialog(
            count = current.count,
            onConfirm = { viewModel.confirmArtistRetag(current.edits) },
            onDismiss = viewModel::dismissDialog,
        )

        is SongDialog.AlbumCoverArtTarget -> AlbumCoverArtTargetDialog(
            onAllSongs = { viewModel.applyAlbumCoverArt(current.artwork, toAllSongs = true) },
            onFirstSong = { viewModel.applyAlbumCoverArt(current.artwork, toAllSongs = false) },
            onDismiss = viewModel::dismissDialog,
        )

        null -> Unit
    }
}

@Composable
private fun SongListHeader(
    title: String,
    artUri: android.net.Uri?,
    author: String?,
    year: Int,
    songCount: Int,
    totalDurationMs: Long,
    cornerShape: androidx.compose.ui.graphics.Shape = RectangleShape,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        AlbumArt(model = artUri, shape = cornerShape, modifier = Modifier.fillMaxSize())

        // Top scrim keeps the back button / status bar legible over bright art.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)),
                ),
        )

        // Bottom scrim + the album/performer/playlist info block.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!author.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = (if (year > 0) "$year  •  " else "") +
                    pluralStringResource(R.plurals.song_count, songCount, songCount) +
                    "  •  " + formatDuration(totalDurationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingTopBar(
    title: String,
    collapsed: Boolean,
    onBack: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onChangeCoverArt: (() -> Unit)?,
    albumHasArt: Boolean,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (collapsed) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        label = "topBarColor",
    )
    val contentColor = if (collapsed) MaterialTheme.colorScheme.onSurface else Color.White
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onEdit != null || onChangeCoverArt != null || onRemove != null

    TopAppBar(
        title = {
            if (collapsed) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onAddToQueue) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.action_add_to_queue),
                )
            }
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.action_add_to_playlist),
                    )
                }
            }
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        onEdit?.let {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = { menuExpanded = false; it() },
                            )
                        }
                        onChangeCoverArt?.let {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (albumHasArt) R.string.action_change_cover_art else R.string.action_add_cover_art,
                                        ),
                                    )
                                },
                                onClick = { menuExpanded = false; it() },
                            )
                        }
                        onRemove?.let {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_remove)) },
                                onClick = { menuExpanded = false; it() },
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = contentColor,
            titleContentColor = contentColor,
            actionIconContentColor = contentColor,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    singleSelectionHasArt: Boolean,
    showRemoveFromPlaylist: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleFavorites: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onDecompose: () -> Unit,
    onChangeCoverArt: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                        onClick = { menuExpanded = false; onAddToPlaylist() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    // "Add" when the lone selected song has no art, else "Change".
                                    if (!singleSelectionHasArt) R.string.action_add_cover_art else R.string.action_change_cover_art,
                                ),
                            )
                        },
                        onClick = { menuExpanded = false; onChangeCoverArt() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit_tags)) },
                        onClick = { menuExpanded = false; onEditTags() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_decompose_title)) },
                        onClick = { menuExpanded = false; onDecompose() },
                    )
                    if (showRemoveFromPlaylist) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_from_playlist)) },
                            onClick = { menuExpanded = false; onRemoveFromPlaylist() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        onClick = { menuExpanded = false; onSelectAll() },
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun PlaybackFabs(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallFloatingActionButton(
            onClick = onShuffle,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.action_shuffle_play))
        }
        ExtendedFloatingActionButton(
            onClick = onPlayAll,
            icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
            text = { Text(stringResource(R.string.action_play_all)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    position: Int,
    albumMode: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    favorite: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        // The currently-playing row gets a subtle primary tint.
        current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            .compositeOver(MaterialTheme.colorScheme.surface)
        else -> MaterialTheme.colorScheme.surface
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            when {
                selectionMode -> Checkbox(checked = selected, onCheckedChange = { onClick() })
                // The playing track shows a speaker in place of its number / cover art.
                current -> Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Albums show the track number (or a note when untagged) instead of art.
                albumMode -> AlbumTrackLeading(song.trackNumber, position)
                else -> AlbumArt(
                    model = song.coverArtModel(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp),
                )
            }
        },
        headlineContent = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (favorite) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun AlbumTrackLeading(trackNumber: Int, position: Int) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (trackNumber > 0) {
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal carousel of the albums a performer authored, newest first. */
@Composable
private fun AuthoredAlbumsSection(
    albums: List<Album>,
    cornerShape: androidx.compose.ui.graphics.Shape,
    onAlbumClick: (Long) -> Unit,
) {
    Column {
        SectionSubheader(stringResource(R.string.albums_authored_section))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickableRow { onAlbumClick(album.id) }
                        .padding(4.dp),
                ) {
                    AlbumArt(
                        model = album.artworkUri,
                        shape = cornerShape,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Text(
                        text = album.title.ifBlank { stringResource(R.string.unknown_album) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (album.year > 0) {
                        Text(
                            text = album.year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionSubheader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun ConfirmRetagDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.retag_confirm_title)) },
        text = { Text(pluralStringResource(R.plurals.retag_confirm_message, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AlbumCoverArtTargetDialog(
    onAllSongs: () -> Unit,
    onFirstSong: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cover_art_target_title)) },
        text = { Text(stringResource(R.string.cover_art_target_message)) },
        confirmButton = {
            TextButton(onClick = onAllSongs) { Text(stringResource(R.string.cover_art_target_all)) }
        },
        dismissButton = {
            TextButton(onClick = onFirstSong) { Text(stringResource(R.string.cover_art_target_first)) }
        },
    )
}

@Composable
private fun EmptyMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.library_empty_songs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun headerTitle(
    source: SongListSource,
    songs: List<Song>,
    userPlaylists: List<UserPlaylist>,
): String = when (source) {
    is SongListSource.AlbumSource ->
        if (source.albumId == pl.dakil.music.domain.model.NO_ALBUM_ID) {
            stringResource(R.string.no_album)
        } else {
            songs.firstOrNull()?.album?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.unknown_album)
        }

    is SongListSource.PerformerSource -> source.name
    is SongListSource.PlaylistSource -> stringResource(systemPlaylistNameRes(source.playlist))
    is SongListSource.UserPlaylistSource ->
        userPlaylists.firstOrNull { it.id == source.playlistId }?.name.orEmpty()
}
