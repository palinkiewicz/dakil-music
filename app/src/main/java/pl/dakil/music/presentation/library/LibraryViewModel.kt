package pl.dakil.music.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.SystemPlaylist

@OptIn(FlowPreview::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val albums: StateFlow<List<Album>> = container.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val performers: StateFlow<List<Performer>> = container.getPerformers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = container.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _systemPlaylistNames = MutableStateFlow<Map<SystemPlaylist, String>>(emptyMap())

    val searchResults: StateFlow<SearchResults> = combine(
        _query.debounce(150).distinctUntilChanged(),
        container.musicRepository.songs,
        albums,
        performers,
        playlists,
        _systemPlaylistNames,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val q = args[0] as String
        @Suppress("UNCHECKED_CAST")
        val songs = args[1] as List<pl.dakil.music.domain.model.Song>
        @Suppress("UNCHECKED_CAST")
        val albumList = args[2] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val performerList = args[3] as List<Performer>
        @Suppress("UNCHECKED_CAST")
        val playlistList = args[4] as List<Playlist>
        @Suppress("UNCHECKED_CAST")
        val names = args[5] as Map<SystemPlaylist, String>

        container.searchLibrary(
            rawQuery = q,
            songs = songs,
            albums = albumList,
            performers = performerList,
            playlists = playlistList,
            playlistDisplayName = { playlist ->
                playlist.userPlaylist?.name
                    ?: playlist.systemType?.let { names[it] }
                    ?: ""
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun setSystemPlaylistNames(names: Map<SystemPlaylist, String>) {
        _systemPlaylistNames.value = names
    }

    fun playSong(song: pl.dakil.music.domain.model.Song) {
        container.playSongs(listOf(song))
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.createPlaylist(name) }
    }

    fun renamePlaylist(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { container.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { container.deletePlaylist(id) }
    }
}
