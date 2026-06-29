package pl.dakil.music.presentation.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import pl.dakil.music.R
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Genre
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.aspectRatioSquare
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.FileInfoDialog
import pl.dakil.music.presentation.components.SelectionTopBar
import pl.dakil.music.presentation.components.clickableRow
import pl.dakil.music.presentation.components.shareSongs
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.playlist.PlaylistNameDialog
import pl.dakil.music.presentation.songlist.DecomposeTitleDialog
import pl.dakil.music.presentation.songlist.EditTagsDialog

private enum class LibraryTab(val titleRes: Int) {
    ALBUMS(R.string.tab_albums),
    PERFORMERS(R.string.tab_performers),
    GENRES(R.string.tab_genres),
    PLAYLISTS(R.string.tab_playlists),
}

@StringRes
fun systemPlaylistNameRes(playlist: SystemPlaylist): Int = when (playlist) {
    SystemPlaylist.ALL_SONGS -> R.string.playlist_all_songs
    SystemPlaylist.FAVORITES -> R.string.playlist_favorites
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tabs = LibraryTab.entries
    val pagerState = rememberPagerState { tabs.size }

    // Hoisted so re-tapping the Library tab can scroll the visible page to the top.
    val albumsGridState = rememberLazyGridState()
    val performersListState = rememberLazyListState()
    val genresListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val albumColumns by viewModel.albumColumns.collectAsStateWithLifecycle()
    val albumCornerDp by viewModel.albumCornerDp.collectAsStateWithLifecycle()
    val performers by viewModel.performers.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
    val genreSort by viewModel.genreSort.collectAsStateWithLifecycle()
    val playlistSort by viewModel.playlistSort.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedSongIds by viewModel.selectedSongIds.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val songDialog by viewModel.dialog.collectAsStateWithLifecycle()
    val coverArtVersion by viewModel.coverArtVersion.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onCoverArtPicked(context.contentResolver, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryEvent.Message ->
                    snackbarHostState.showSnackbar(resources.getString(event.res))

                is LibraryEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    var creatingPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserPlaylist?>(null) }
    var deleting by remember { mutableStateOf<UserPlaylist?>(null) }

    val allSongsName = stringResource(R.string.playlist_all_songs)
    val favoritesName = stringResource(R.string.playlist_favorites)
    LaunchedEffect(allSongsName, favoritesName) {
        viewModel.setSystemPlaylistNames(
            mapOf(
                SystemPlaylist.ALL_SONGS to allSongsName,
                SystemPlaylist.FAVORITES to favoritesName,
            ),
        )
    }

    // Back exits search instead of leaving the app — but only once the keyboard is
    // down. While the IME is up, back is left to the system so it closes the keyboard
    // first. Covers button, gesture and predictive back alike.
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = selectedSongIds.isNotEmpty()) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = selectedSongIds.isEmpty() && query.isNotEmpty() && !imeVisible) {
        viewModel.clearQuery()
    }

    // Re-tapping the Library tab exits an active search, otherwise scrolls the
    // currently visible page back to the top.
    LaunchedEffect(onReselect) {
        onReselect.collect {
            if (viewModel.query.value.isNotEmpty()) {
                viewModel.clearQuery()
            } else {
                when (tabs[pagerState.currentPage]) {
                    LibraryTab.ALBUMS -> albumsGridState.animateScrollToItem(0)
                    LibraryTab.PERFORMERS -> performersListState.animateScrollToItem(0)
                    LibraryTab.GENRES -> genresListState.animateScrollToItem(0)
                    LibraryTab.PLAYLISTS -> playlistsListState.animateScrollToItem(0)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (query.isBlank()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier.zIndex(2f)) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs[page]) {
                    LibraryTab.ALBUMS -> AlbumsGrid(
                        albums = albums,
                        columns = albumColumns,
                        cornerDp = albumCornerDp,
                        sort = albumSort,
                        gridState = albumsGridState,
                        onSortSelect = viewModel::selectAlbumSort,
                        onClick = onAlbumClick,
                    )
                    LibraryTab.PERFORMERS -> PerformersList(
                        performers = performers,
                        sort = artistSort,
                        listState = performersListState,
                        onSortSelect = viewModel::selectArtistSort,
                        onClick = onPerformerClick,
                    )
                    LibraryTab.GENRES -> GenresList(
                        genres = genres,
                        sort = genreSort,
                        listState = genresListState,
                        onSortSelect = viewModel::selectGenreSort,
                        onClick = onGenreClick,
                    )
                    LibraryTab.PLAYLISTS -> PlaylistsList(
                        playlists = playlists,
                        sort = playlistSort,
                        listState = playlistsListState,
                        onSortSelect = { viewModel.selectPlaylistSort(it, mapOf(
                            SystemPlaylist.ALL_SONGS to allSongsName,
                            SystemPlaylist.FAVORITES to favoritesName,
                        )) },
                        onSystemClick = onPlaylistClick,
                        onUserClick = onUserPlaylistClick,
                        onCreate = { creatingPlaylist = true },
                        onRename = { renaming = it },
                        onDelete = { deleting = it },
                    )
                }
            }
        } else {
            SearchResultsList(
                results = searchResults,
                selectedSongIds = selectedSongIds,
                onSongClick = viewModel::onSongClick,
                onSongLongClick = viewModel::onSongLongClick,
                onAlbumClick = onAlbumClick,
                onPerformerClick = onPerformerClick,
                onPlaylistClick = onPlaylistClick,
                onUserPlaylistClick = onUserPlaylistClick,
                query = query,
                coverArtVersion = coverArtVersion,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

        // Selection overlay covers the search bar while songs are picked for a bulk action.
        if (selectedSongIds.isNotEmpty()) {
            val selectedSongs = searchResults.songs.filter { it.id in selectedSongIds }
            SelectionTopBar(
                selectedCount = selectedSongIds.size,
                allSelectedFavorite = selectedSongIds.isNotEmpty() &&
                    selectedSongIds.all { it in favoriteIds },
                singleSelectionHasArt = selectedSongs.size == 1 &&
                    selectedSongs.first().albumArtUri != null,
                showRemoveFromPlaylist = false,
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onToggleFavorites = viewModel::toggleFavoritesForSelection,
                onAddToQueue = viewModel::addSelectionToQueue,
                onAddToPlaylist = viewModel::startAddToPlaylist,
                onEditTags = viewModel::startEditTags,
                onDecompose = viewModel::startDecompose,
                onChangeCoverArt = { imagePicker.launch(arrayOf("image/png", "image/jpeg")) },
                onRemoveFromPlaylist = {},
                onShare = {
                    shareSongs(context, selectedSongs)
                    viewModel.clearSelection()
                },
                onShowInfo = viewModel::startFileInfo,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (val current = songDialog) {
        is LibrarySongDialog.EditTags -> EditTagsDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onSave = { edit -> viewModel.saveTags(current.songs, edit) },
        )

        is LibrarySongDialog.Decompose -> DecomposeTitleDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onApply = { options -> viewModel.applyDecomposition(current.songs, options) },
        )

        is LibrarySongDialog.AddToPlaylist -> AddToPlaylistDialog(
            playlists = userPlaylists,
            onDismiss = viewModel::dismissDialog,
            onSelect = { id -> viewModel.addToExistingPlaylist(id, current.songs) },
            onCreateNew = { name -> viewModel.createPlaylistAndAdd(name, current.songs) },
        )

        is LibrarySongDialog.FileInfo -> FileInfoDialog(
            infos = current.infos,
            onDismiss = viewModel::dismissDialog,
        )

        null -> Unit
    }

    if (creatingPlaylist) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_new,
            confirmRes = R.string.playlist_create,
            initialName = "",
            onDismiss = { creatingPlaylist = false },
            onConfirm = {
                viewModel.createPlaylist(it)
                creatingPlaylist = false
            },
        )
    }

    renaming?.let { playlist ->
        PlaylistNameDialog(
            titleRes = R.string.playlist_rename,
            confirmRes = R.string.playlist_rename_confirm,
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renamePlaylist(playlist.id, it)
                renaming = null
            },
        )
    }

    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message, playlist.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    deleting = null
                }) {
                    Text(stringResource(R.string.playlist_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.edit_tags_cancel))
                }
            },
        )
    }
}

private const val PAGE_SIZE = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    results: SearchResults,
    selectedSongIds: Set<Long>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    query: String,
    coverArtVersion: Int,
    modifier: Modifier = Modifier,
) {
    val selectionMode = selectedSongIds.isNotEmpty()
    var songsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var albumsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var artistsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var playlistsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }

    if (results.isEmpty) {
        EmptyState(stringResource(R.string.search_no_results), Icons.Rounded.Search)
        return
    }

    LazyColumn(modifier = modifier) {
        if (results.songs.isNotEmpty()) {
            item(key = "header_songs") {
                SectionHeader(stringResource(R.string.search_section_songs))
            }
            items(results.songs.take(songsVisible), key = { "song_${it.id}" }) { song ->
                val selected = song.id in selectedSongIds
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            song.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                ?: stringResource(R.string.unknown_artist),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        if (selectionMode) {
                            Checkbox(checked = selected, onCheckedChange = { onSongClick(song) })
                        } else {
                            AlbumArt(
                                model = song.coverArtModel(coverArtVersion),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song.id) },
                    ),
                )
            }
            if (results.songs.size > songsVisible) {
                item(key = "more_songs") {
                    ViewMoreButton { songsVisible += PAGE_SIZE }
                }
            }
        }

        if (results.albums.isNotEmpty()) {
            item(key = "header_albums") {
                SectionHeader(stringResource(R.string.tab_albums))
            }
            items(results.albums.take(albumsVisible), key = { "album_${it.id}" }) { album ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = album.title.ifBlank { stringResource(R.string.unknown_album) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        AlbumArt(
                            model = album.artworkUri,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    modifier = Modifier.clickableRow { onAlbumClick(album.id) },
                )
            }
            if (results.albums.size > albumsVisible) {
                item(key = "more_albums") {
                    ViewMoreButton { albumsVisible += PAGE_SIZE }
                }
            }
        }

        if (results.artists.isNotEmpty()) {
            item(key = "header_artists") {
                SectionHeader(stringResource(R.string.tab_performers))
            }
            items(results.artists.take(artistsVisible), key = { "artist_${it.name}" }) { artist ->
                ListItem(
                    headlineContent = {
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.song_count,
                                artist.songCount,
                                artist.songCount,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow { onPerformerClick(artist.name) },
                )
            }
            if (results.artists.size > artistsVisible) {
                item(key = "more_artists") {
                    ViewMoreButton { artistsVisible += PAGE_SIZE }
                }
            }
        }

        if (results.playlists.isNotEmpty()) {
            item(key = "header_playlists") {
                SectionHeader(stringResource(R.string.tab_playlists))
            }
            items(results.playlists.take(playlistsVisible), key = { "playlist_${it.systemType?.name ?: it.userPlaylist!!.id}" }) { playlist ->
                val userPlaylist = playlist.userPlaylist
                ListItem(
                    headlineContent = {
                        Text(
                            text = userPlaylist?.name
                                ?: stringResource(systemPlaylistNameRes(playlist.systemType!!)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.song_count,
                                playlist.songCount,
                                playlist.songCount,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = when {
                                playlist.systemType == SystemPlaylist.FAVORITES -> Icons.Rounded.Favorite
                                playlist.systemType == SystemPlaylist.ALL_SONGS -> Icons.Rounded.MusicNote
                                else -> Icons.Rounded.QueueMusic
                            },
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickableRow {
                        if (userPlaylist != null) onUserPlaylistClick(userPlaylist.id)
                        else onPlaylistClick(playlist.systemType!!)
                    },
                )
            }
            if (results.playlists.size > playlistsVisible) {
                item(key = "more_playlists") {
                    ViewMoreButton { playlistsVisible += PAGE_SIZE }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ViewMoreButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.search_view_more))
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    columns: Int,
    cornerDp: Int,
    sort: SortState<AlbumSortOption>,
    gridState: LazyGridState,
    onSortSelect: (AlbumSortOption) -> Unit,
    onClick: (Long) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_albums), Icons.Rounded.LibraryMusic)
        return
    }
    LaunchedEffect(sort) { gridState.scrollToItem(0) }

    ScrollAwareSortHeader(
        sortChips = {
            SortChipRow(options = AlbumSortOption.entries, sort = sort, onSelect = onSortSelect)
        },
    ) { topPadding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = topPadding + 8.dp,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, cornerDp, onClick)
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, cornerDp: Int, onClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickableRow { onClick(album.id) }
            .padding(8.dp),
    ) {
        AlbumArt(
            model = album.artworkUri,
            shape = RoundedCornerShape(cornerDp.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatioSquare(),
        )
        Text(
            text = if (album.isNoAlbum) {
                stringResource(R.string.no_album)
            } else {
                album.title.ifBlank { stringResource(R.string.unknown_album) }
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!album.isNoAlbum) {
            Text(
                text = album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PerformersList(
    performers: List<Performer>,
    sort: SortState<ArtistSortOption>,
    listState: LazyListState,
    onSortSelect: (ArtistSortOption) -> Unit,
    onClick: (String) -> Unit,
) {
    if (performers.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_performers), Icons.Rounded.Person)
        return
    }
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    ScrollAwareSortHeader(
        sortChips = {
            SortChipRow(options = ArtistSortOption.entries, sort = sort, onSelect = onSortSelect)
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(performers, key = { it.name }) { performer ->
                ListItem(
                    headlineContent = { Text(performer.name) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.song_count,
                                performer.songCount,
                                performer.songCount,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow { onClick(performer.name) },
                )
            }
        }
    }
}

@Composable
private fun GenresList(
    genres: List<Genre>,
    sort: SortState<GenreSortOption>,
    listState: LazyListState,
    onSortSelect: (GenreSortOption) -> Unit,
    onClick: (String) -> Unit,
) {
    if (genres.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_genres), Icons.Rounded.Category)
        return
    }
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    ScrollAwareSortHeader(
        sortChips = {
            SortChipRow(options = GenreSortOption.entries, sort = sort, onSelect = onSortSelect)
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(genres, key = { it.name }) { genre ->
                ListItem(
                    headlineContent = { Text(genre.name) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.song_count,
                                genre.songCount,
                                genre.songCount,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Category, contentDescription = null)
                    },
                    modifier = Modifier.clickableRow { onClick(genre.name) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    sort: SortState<PlaylistSortOption>,
    listState: LazyListState,
    onSortSelect: (PlaylistSortOption) -> Unit,
    onSystemClick: (SystemPlaylist) -> Unit,
    onUserClick: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (UserPlaylist) -> Unit,
    onDelete: (UserPlaylist) -> Unit,
) {
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    ScrollAwareSortHeader(
        sortChips = {
            SortChipRow(options = PlaylistSortOption.entries, sort = sort, onSelect = onSortSelect)
        },
    ) { topPadding ->
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "create") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.playlist_new)) },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickableRow(onCreate),
            )
        }
        items(playlists, key = { it.systemType?.name ?: it.userPlaylist!!.id }) { playlist ->
            val count = pluralStringResource(
                R.plurals.song_count,
                playlist.songCount,
                playlist.songCount,
            )
            val userPlaylist = playlist.userPlaylist
            ListItem(
                headlineContent = {
                    Text(
                        text = userPlaylist?.name
                            ?: stringResource(systemPlaylistNameRes(playlist.systemType!!)),
                    )
                },
                supportingContent = { Text(count) },
                leadingContent = {
                    Icon(
                        imageVector = when {
                            playlist.systemType == SystemPlaylist.FAVORITES -> Icons.Rounded.Favorite
                            playlist.systemType == SystemPlaylist.ALL_SONGS -> Icons.Rounded.MusicNote
                            else -> Icons.Rounded.QueueMusic
                        },
                        contentDescription = null,
                    )
                },
                trailingContent = userPlaylist?.let {
                    {
                        Row {
                            IconButton(onClick = { onRename(it) }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.playlist_rename),
                                )
                            }
                            IconButton(onClick = { onDelete(it) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.playlist_delete),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.clickableRow {
                    if (userPlaylist != null) onUserClick(userPlaylist.id)
                    else onSystemClick(playlist.systemType!!)
                },
            )
        }
    }
    }
}

@Composable
private fun ScrollAwareSortHeader(
    sortChips: @Composable () -> Unit,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    var chipHeightPx by remember { mutableIntStateOf(0) }
    var chipOffsetYPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val oldOffset = chipOffsetYPx
                chipOffsetYPx = (chipOffsetYPx + available.y).coerceIn(-chipHeightPx.toFloat(), 0f)
                val consumed = chipOffsetYPx - oldOffset
                return Offset(0f, consumed)
            }
        }
    }

    Box(modifier = Modifier.zIndex(1f).fillMaxSize().nestedScroll(nestedScrollConnection)) {
        val topPadding = with(density) { (chipHeightPx + chipOffsetYPx).coerceAtLeast(0f).toDp() }
        content(topPadding)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { chipHeightPx = it.height }
                .offset { IntOffset(0, chipOffsetYPx.roundToInt()) }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            sortChips()
        }
    }
}

@Composable
private fun <T : Enum<T>> SortChipRow(
    options: List<T>,
    sort: SortState<T>,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = option == sort.option
            FilterChip(
                selected = selected,
                onClick = { onSelect(option) },
                label = {
                    val labelRes = when (option) {
                        is AlbumSortOption -> option.labelRes
                        is ArtistSortOption -> option.labelRes
                        is PlaylistSortOption -> option.labelRes
                        else -> 0
                    }
                    Text(stringResource(labelRes))
                },
                trailingIcon = {
                    if (selected) {
                        Icon(
                            imageVector = if (sort.direction == SortDirection.ASC) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = stringResource(
                                if (sort.direction == SortDirection.ASC) {
                                    R.string.sort_direction_asc
                                } else {
                                    R.string.sort_direction_desc
                                },
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
