package pl.dakil.music.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = LibraryTab.entries

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val performers by viewModel.performers.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // null = no dialog; "" = create; otherwise the id of the playlist being renamed.
    var creatingPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserPlaylist?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(tab.titleRes)) },
                )
            }
        }

        when (tabs[selectedTab]) {
            LibraryTab.ALBUMS -> AlbumsGrid(albums, onAlbumClick)
            LibraryTab.PERFORMERS -> PerformersList(performers, onPerformerClick)
            LibraryTab.PLAYLISTS -> PlaylistsList(
                playlists = playlists,
                onSystemClick = onPlaylistClick,
                onUserClick = onUserPlaylistClick,
                onCreate = { creatingPlaylist = true },
                onRename = { renaming = it },
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
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onClick: (Long) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_albums), Icons.Rounded.LibraryMusic)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
private fun PerformersList(performers: List<Performer>, onClick: (String) -> Unit) {
    if (performers.isEmpty()) {
        EmptyState(stringResource(R.string.library_empty_performers), Icons.Rounded.Person)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    onSystemClick: (SystemPlaylist) -> Unit,
    onUserClick: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (UserPlaylist) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                        IconButton(onClick = { onRename(it) }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.playlist_rename),
                            )
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
