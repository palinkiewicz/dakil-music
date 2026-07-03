package pl.dakil.music.presentation.songlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.MusicApplication
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.presentation.navigation.Routes
import pl.dakil.music.presentation.navigation.SourceType

/**
 * Hosts [SongListScreen] for a system playlist (Favourites / All songs) with a
 * source-scoped ViewModel, so it can be reached both as a standalone destination and
 * embedded as a Library tab without going through the parameterised song-list route.
 */
@Composable
fun SystemPlaylistSongListScreen(
    playlist: SystemPlaylist,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    showBack: Boolean = true,
) {
    val app = LocalContext.current.applicationContext as MusicApplication
    val factory = remember(playlist) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SongListViewModel(
                    app.container,
                    SavedStateHandle(
                        mapOf(
                            Routes.ARG_SOURCE_TYPE to SourceType.PLAYLIST.name,
                            Routes.ARG_SOURCE_ARG to playlist.name,
                        ),
                    ),
                ) as T
        }
    }
    SongListScreen(
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        modifier = modifier,
        embedded = embedded,
        showBack = showBack,
        viewModel = viewModel(key = "system_playlist_${playlist.name}", factory = factory),
    )
}
