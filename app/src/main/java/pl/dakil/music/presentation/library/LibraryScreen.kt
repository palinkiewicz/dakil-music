package pl.dakil.music.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import pl.dakil.music.R
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.aspectRatioSquare
import pl.dakil.music.presentation.components.clickableRow
import pl.dakil.music.presentation.playlist.PlaylistNameDialog

private enum class LibraryTab(val titleRes: Int) {
    ALBUMS(R.string.tab_albums),
    PERFORMERS(R.string.tab_performers),
    PLAYLISTS(R.string.tab_playlists),
}

@StringRes
fun systemPlaylistNameRes(playlist: SystemPlaylist): Int = when (playlist) {
    SystemPlaylist.ALL_SONGS -> R.string.playlist_all_songs
    SystemPlaylist.FAVORITES -> R.string.playlist_favorites
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tabs = LibraryTab.entries
    val pagerState = rememberPagerState { tabs.size }

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val performers by viewModel.performers.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
    val playlistSort by viewModel.playlistSort.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

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

    Column(modifier = modifier.fillMaxSize()) {
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
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
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
                        sort = albumSort,
                        onSortSelect = viewModel::selectAlbumSort,
                        onClick = onAlbumClick,
                    )
                    LibraryTab.PERFORMERS -> PerformersList(
                        performers = performers,
                        sort = artistSort,
                        onSortSelect = viewModel::selectArtistSort,
                        onClick = onPerformerClick,
                    )
                    LibraryTab.PLAYLISTS -> PlaylistsList(
                        playlists = playlists,
                        sort = playlistSort,
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
                onSongClick = viewModel::playSong,
                onAlbumClick = onAlbumClick,
                onPerformerClick = onPerformerClick,
                onPlaylistClick = onPlaylistClick,
                onUserPlaylistClick = onUserPlaylistClick,
                query = query,
                modifier = Modifier.fillMaxSize(),
            )
        }
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

@Composable
private fun SearchResultsList(
    results: SearchResults,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    query: String,
    modifier: Modifier = Modifier,
) {
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
                ListItem(
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
                        AlbumArt(
                            uri = song.albumArtUri,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    modifier = Modifier.clickableRow { onSongClick(song) },
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
                            uri = album.artworkUri,
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
    sort: SortState<AlbumSortOption>,
    onSortSelect: (AlbumSortOption) -> Unit,
    onClick: (Long) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_albums), Icons.Rounded.LibraryMusic)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort_row", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            SortChipRow(
                options = AlbumSortOption.entries,
                sort = sort,
                onSelect = onSortSelect,
            )
        }
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, onClick)
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickableRow { onClick(album.id) }
            .padding(8.dp),
    ) {
        AlbumArt(
            uri = album.artworkUri,
            shape = MaterialTheme.shapes.large,
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
    onSortSelect: (ArtistSortOption) -> Unit,
    onClick: (String) -> Unit,
) {
    if (performers.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_performers), Icons.Rounded.Person)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "sort_row") {
            SortChipRow(
                options = ArtistSortOption.entries,
                sort = sort,
                onSelect = onSortSelect,
            )
        }
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

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    sort: SortState<PlaylistSortOption>,
    onSortSelect: (PlaylistSortOption) -> Unit,
    onSystemClick: (SystemPlaylist) -> Unit,
    onUserClick: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (UserPlaylist) -> Unit,
    onDelete: (UserPlaylist) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "sort_row") {
            SortChipRow(
                options = PlaylistSortOption.entries,
                sort = sort,
                onSelect = onSortSelect,
            )
        }
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
