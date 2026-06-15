package pl.dakil.music.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist

@OptIn(FlowPreview::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    // --- Sort state -----------------------------------------------------------------

    private val _albumSort = MutableStateFlow(SortState(AlbumSortOption.ALBUM_NAME))
    val albumSort: StateFlow<SortState<AlbumSortOption>> = _albumSort

    private val _artistSort = MutableStateFlow(SortState(ArtistSortOption.ARTIST_NAME))
    val artistSort: StateFlow<SortState<ArtistSortOption>> = _artistSort

    private val _playlistSort = MutableStateFlow(SortState(PlaylistSortOption.PLAYLIST_NAME))
    val playlistSort: StateFlow<SortState<PlaylistSortOption>> = _playlistSort

    init {
        viewModelScope.launch {
            val rememberSort = container.observeSettings().first().rememberSortState
            if (rememberSort) {
                _albumSort.value = container.sortStateRepository.loadAlbumSort()
                _artistSort.value = container.sortStateRepository.loadArtistSort()
                _playlistSort.value = container.sortStateRepository.loadPlaylistSort()
            }
        }
    }

    fun selectAlbumSort(option: AlbumSortOption) {
        _albumSort.value = _albumSort.value.select(option)
        persistSortIfEnabled()
    }

    fun selectArtistSort(option: ArtistSortOption) {
        _artistSort.value = _artistSort.value.select(option)
        persistSortIfEnabled()
    }

    fun selectPlaylistSort(option: PlaylistSortOption, systemPlaylistNames: Map<SystemPlaylist, String>) {
        _playlistSort.value = _playlistSort.value.select(option)
        _systemPlaylistNames.value = systemPlaylistNames
        persistSortIfEnabled()
    }

    private fun persistSortIfEnabled() {
        viewModelScope.launch {
            if (container.observeSettings().first().rememberSortState) {
                container.sortStateRepository.saveAlbumSort(_albumSort.value)
                container.sortStateRepository.saveArtistSort(_artistSort.value)
                container.sortStateRepository.savePlaylistSort(_playlistSort.value)
            }
        }
    }

    // --- Data flows -----------------------------------------------------------------

    val albumColumns: StateFlow<Int> = container.observeSettings()
        .map { it.albumColumns }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    val albums: StateFlow<List<Album>> = combine(
        container.getAlbums(),
        _albumSort,
    ) { list, sort ->
        val comparator: Comparator<Album> = when (sort.option) {
            AlbumSortOption.ALBUM_NAME -> compareBy { it.title.lowercase() }
            AlbumSortOption.ARTIST_NAME -> compareBy { it.artist.lowercase() }
            AlbumSortOption.SONG_COUNT -> compareBy { it.songCount }
            AlbumSortOption.DURATION -> compareBy { it.durationMs }
        }
        val noAlbum = list.filter { it.isNoAlbum }
        val real = list.filter { !it.isNoAlbum }.sortedWith(
            if (sort.direction == SortDirection.DESC) comparator.reversed() else comparator,
        )
        noAlbum + real
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val performers: StateFlow<List<Performer>> = combine(
        container.getPerformers(),
        _artistSort,
    ) { list, sort ->
        val comparator: Comparator<Performer> = when (sort.option) {
            ArtistSortOption.ARTIST_NAME -> compareBy { it.name.lowercase() }
            ArtistSortOption.SONG_COUNT -> compareBy { it.songCount }
        }
        list.sortedWith(if (sort.direction == SortDirection.DESC) comparator.reversed() else comparator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rawPlaylists = container.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _systemPlaylistNames = MutableStateFlow<Map<SystemPlaylist, String>>(emptyMap())

    val playlists: StateFlow<List<Playlist>> = combine(
        _rawPlaylists,
        _playlistSort,
        _systemPlaylistNames,
    ) { list, sort, names ->
        fun displayName(p: Playlist) = p.userPlaylist?.name ?: p.systemType?.let { names[it] } ?: ""
        val comparator: Comparator<Playlist> = when (sort.option) {
            PlaylistSortOption.PLAYLIST_NAME -> compareBy { displayName(it).lowercase() }
            PlaylistSortOption.SONG_COUNT -> compareBy { it.songCount }
            PlaylistSortOption.DURATION -> compareBy { it.durationMs }
        }
        list.sortedWith(if (sort.direction == SortDirection.DESC) comparator.reversed() else comparator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Search ---------------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val searchResults: StateFlow<SearchResults> = combine(
        _query.debounce(150).distinctUntilChanged(),
        container.musicRepository.songs,
        albums,
        performers,
        _rawPlaylists,
        _systemPlaylistNames,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val q = args[0] as String
        @Suppress("UNCHECKED_CAST")
        val songs = args[1] as List<Song>
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

    fun playSong(song: Song) {
        container.playSongs(listOf(song))
    }

    // --- Playlist management --------------------------------------------------------

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
