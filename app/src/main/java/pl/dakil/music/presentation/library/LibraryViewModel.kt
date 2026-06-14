package pl.dakil.music.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val albums: StateFlow<List<Album>> = container.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val performers: StateFlow<List<Performer>> = container.getPerformers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = container.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
