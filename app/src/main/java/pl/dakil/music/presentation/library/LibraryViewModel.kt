package pl.dakil.music.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.StatisticsWindow
import pl.dakil.music.domain.model.SystemPlaylist
import java.time.DayOfWeek

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

    val albumCornerDp: StateFlow<Int> = container.observeSettings()
        .map { it.albumCornerRoundnessDp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 16)

    // All-time listening totals, recomputed when history changes, used by the
    // "Listening duration" / "Tracks played" sort options (same ranking as Statistics).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allTimeStats: StateFlow<Statistics> = container.observeHistoryChanges()
        .onStart { emit(Unit) }
        .mapLatest {
            container.getStatistics(
                StatisticsWindow(0L, Long.MAX_VALUE),
                StatMetric.SECONDS,
                DayOfWeek.MONDAY,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Statistics.EMPTY)

    private val albumSecondsById = allTimeStats.map { stats ->
        stats.topAlbums.associate { it.albumId to (it.seconds to it.plays) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val artistStatsByName = allTimeStats.map { stats ->
        stats.topArtists.associate { it.name.lowercase() to (it.seconds to it.plays) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val albums: StateFlow<List<Album>> = combine(
        container.getAlbums(),
        _albumSort,
        albumSecondsById,
    ) { list, sort, stats ->
        fun seconds(a: Album) = stats[a.id]?.first ?: 0L
        fun plays(a: Album) = stats[a.id]?.second ?: 0L
        val comparator: Comparator<Album> = when (sort.option) {
            AlbumSortOption.ALBUM_NAME -> compareBy { it.title.lowercase() }
            AlbumSortOption.ARTIST_NAME -> compareBy { it.artist.lowercase() }
            AlbumSortOption.SONG_COUNT -> compareBy { it.songCount }
            AlbumSortOption.DURATION -> compareBy { it.durationMs }
            AlbumSortOption.RELEASE_YEAR -> compareBy { it.year }
            AlbumSortOption.LISTENING_DURATION -> compareBy { seconds(it) }
            AlbumSortOption.TRACKS_PLAYED -> compareBy { plays(it) }
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
        artistStatsByName,
    ) { list, sort, stats ->
        fun seconds(p: Performer) = stats[p.name.lowercase()]?.first ?: 0L
        fun plays(p: Performer) = stats[p.name.lowercase()]?.second ?: 0L
        val comparator: Comparator<Performer> = when (sort.option) {
            ArtistSortOption.ARTIST_NAME -> compareBy { it.name.lowercase() }
            ArtistSortOption.SONG_COUNT -> compareBy { it.songCount }
            ArtistSortOption.LISTENING_DURATION -> compareBy { seconds(it) }
            ArtistSortOption.TRACKS_PLAYED -> compareBy { plays(it) }
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
