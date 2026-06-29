package pl.dakil.music.presentation.library

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Genre
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.StatisticsWindow
import pl.dakil.music.domain.model.SongFileInfo
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.repository.ArtworkData
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.domain.util.DecomposeOptions
import pl.dakil.music.domain.util.TitleDecomposer
import java.time.DayOfWeek

/** Which modal dialog (if any) is shown over the library for the song selection. */
sealed interface LibrarySongDialog {
    data class EditTags(val songs: List<Song>) : LibrarySongDialog
    data class Decompose(val songs: List<Song>) : LibrarySongDialog
    data class AddToPlaylist(val songs: List<Song>) : LibrarySongDialog

    /** Read-only filesystem details for the selected song(s). */
    data class FileInfo(val infos: List<SongFileInfo>) : LibrarySongDialog
}

/** One-shot effects the library screen consumes (snackbars, Scoped-Storage consent). */
sealed interface LibraryEvent {
    data class Message(@param:StringRes val res: Int) : LibraryEvent
    data class RequestWritePermission(val intentSender: android.content.IntentSender) : LibraryEvent
}

@OptIn(FlowPreview::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    // --- Sort state -----------------------------------------------------------------

    private val _albumSort = MutableStateFlow(SortState(AlbumSortOption.ALBUM_NAME))
    val albumSort: StateFlow<SortState<AlbumSortOption>> = _albumSort

    private val _artistSort = MutableStateFlow(SortState(ArtistSortOption.ARTIST_NAME))
    val artistSort: StateFlow<SortState<ArtistSortOption>> = _artistSort

    private val _playlistSort = MutableStateFlow(SortState(PlaylistSortOption.PLAYLIST_NAME))
    val playlistSort: StateFlow<SortState<PlaylistSortOption>> = _playlistSort

    // Genre sort is session-only (not persisted alongside the album/artist/playlist sorts).
    private val _genreSort = MutableStateFlow(SortState(GenreSortOption.GENRE_NAME))
    val genreSort: StateFlow<SortState<GenreSortOption>> = _genreSort

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

    fun selectGenreSort(option: GenreSortOption) {
        _genreSort.value = _genreSort.value.select(option)
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

    val genres: StateFlow<List<Genre>> = combine(
        container.getGenres(),
        _genreSort,
    ) { list, sort ->
        val comparator: Comparator<Genre> = when (sort.option) {
            GenreSortOption.GENRE_NAME -> compareBy { it.name.lowercase() }
            GenreSortOption.SONG_COUNT -> compareBy { it.songCount }
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
        clearSelection()
    }

    fun clearQuery() {
        _query.value = ""
        clearSelection()
    }

    fun setSystemPlaylistNames(names: Map<SystemPlaylist, String>) {
        _systemPlaylistNames.value = names
    }

    fun playSong(song: Song) {
        container.playSongs(listOf(song))
    }

    // --- Song selection (search view) -----------------------------------------------

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()

    val favoriteIds: StateFlow<Set<Long>> = container.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val userPlaylists: StateFlow<List<UserPlaylist>> = container.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow<LibrarySongDialog?>(null)
    val dialog: StateFlow<LibrarySongDialog?> = _dialog.asStateFlow()

    /** Bumped after a cover-art write so the UI re-fetches embedded art (busts Coil's cache). */
    private val _coverArtVersion = MutableStateFlow(0)
    val coverArtVersion: StateFlow<Int> = _coverArtVersion.asStateFlow()

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    /** A tag-write retried verbatim after the user grants Scoped-Storage consent. */
    private var pendingWrite: (suspend () -> TagWriteResult)? = null
    private var pendingWriteIds: List<Long> = emptyList()
    private var pendingPostSuccess: (suspend () -> Unit)? = null

    /** Tap a search song: toggle it while selecting, otherwise play it. */
    fun onSongClick(song: Song) {
        if (_selectedSongIds.value.isNotEmpty()) toggleSelection(song.id) else playSong(song)
    }

    fun onSongLongClick(songId: Long) = toggleSelection(songId)

    private fun toggleSelection(songId: Long) {
        _selectedSongIds.update { current ->
            if (songId in current) current - songId else current + songId
        }
    }

    fun clearSelection() {
        _selectedSongIds.value = emptySet()
    }

    /** Selects every song currently shown in the search results. */
    fun selectAll() {
        _selectedSongIds.value = searchResults.value.songs.mapTo(HashSet()) { it.id }
    }

    private fun selectedSongs(): List<Song> {
        val selected = _selectedSongIds.value
        return searchResults.value.songs.filter { it.id in selected }
    }

    fun addSelectionToQueue() {
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        container.addToQueue(songs)
        viewModelScope.launch { _events.send(LibraryEvent.Message(R.string.snackbar_added_to_queue)) }
        clearSelection()
    }

    /**
     * Adds the selection to favorites, or — when every selected song is already a
     * favorite — removes them all instead.
     */
    fun toggleFavoritesForSelection() {
        val ids = _selectedSongIds.value
        if (ids.isEmpty()) return
        val makeFavorite = !ids.all { it in favoriteIds.value }
        viewModelScope.launch {
            container.setFavorites(ids, favorite = makeFavorite)
            _events.send(
                LibraryEvent.Message(
                    if (makeFavorite) {
                        R.string.snackbar_added_to_favorites
                    } else {
                        R.string.snackbar_removed_from_favorites
                    },
                ),
            )
            clearSelection()
        }
    }

    fun startAddToPlaylist() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = LibrarySongDialog.AddToPlaylist(songs)
    }

    fun addToExistingPlaylist(playlistId: String, songs: List<Song>) {
        viewModelScope.launch {
            container.addSongsToPlaylist(playlistId, songs.map { it.id })
            afterPlaylistAdd()
        }
    }

    fun createPlaylistAndAdd(name: String, songs: List<Song>) {
        viewModelScope.launch {
            val id = container.createPlaylist(name)
            container.addSongsToPlaylist(id, songs.map { it.id })
            afterPlaylistAdd()
        }
    }

    private suspend fun afterPlaylistAdd() {
        _events.send(LibraryEvent.Message(R.string.snackbar_added_to_playlist))
        _dialog.value = null
        clearSelection()
    }

    fun startEditTags() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = LibrarySongDialog.EditTags(songs)
    }

    fun startDecompose() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = LibrarySongDialog.Decompose(songs)
    }

    /** Resolves filesystem details for the selection and shows the info dialog. */
    fun startFileInfo() {
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val infos = container.getSongFileInfo(songs)
            if (infos.isNotEmpty()) _dialog.value = LibrarySongDialog.FileInfo(infos)
        }
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun saveTags(songs: List<Song>, edit: TagEdit) {
        if (edit.isEmpty || songs.isEmpty()) {
            dismissDialog()
            return
        }
        performWrite(songs.map { it.id }) { container.editTags(songs, edit) }
    }

    /**
     * Runs [TitleDecomposer] over each selected song with the same [options] and writes
     * the resulting per-song title + artists. Songs that yield no extracted performers
     * are left untouched.
     */
    fun applyDecomposition(songs: List<Song>, options: DecomposeOptions) {
        val edits = songs.mapNotNull { song ->
            val result = TitleDecomposer.decompose(song.title, options)
            if (result.artists.isEmpty()) {
                null
            } else {
                SongTagEdit(
                    song = song,
                    edit = TagEdit(
                        title = result.title.takeIf { it.isNotBlank() },
                        artist = result.artists.joinToString(", ").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        if (edits.isEmpty()) {
            dismissDialog()
            return
        }
        performWrite(edits.map { it.song.id }) { container.editTags(edits) }
    }

    /**
     * Validates a picked image and applies it to the current selection. The native
     * picker is launched from the screen, which routes the chosen URI back here.
     */
    fun onCoverArtPicked(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val artwork = readArtwork(resolver, uri)
            if (artwork == null) {
                _events.send(LibraryEvent.Message(R.string.cover_art_invalid))
                return@launch
            }
            val songs = selectedSongs()
            if (songs.isNotEmpty()) writeArtwork(songs, artwork)
        }
    }

    private fun writeArtwork(songs: List<Song>, artwork: ArtworkData) {
        performWrite(
            affectedIds = songs.map { it.id },
            onSuccess = {
                container.coverArtRefresher.invalidate(songs)
                _coverArtVersion.update { it + 1 }
            },
        ) { container.editTags(songs, TagEdit(artwork = artwork)) }
    }

    private suspend fun readArtwork(resolver: ContentResolver, uri: Uri): ArtworkData? =
        withContext(Dispatchers.IO) {
            val mime = resolver.getType(uri)
            if (mime != "image/png" && mime != "image/jpeg") return@withContext null
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty()) null else ArtworkData(bytes, mime)
        }

    /** Called by the screen after the user grants Scoped-Storage write consent. */
    fun onWritePermissionGranted() {
        val retry = pendingWrite ?: return
        val post = pendingPostSuccess
        pendingWrite = null
        pendingPostSuccess = null
        performWrite(pendingWriteIds, post, retry)
    }

    /**
     * Shared write pipeline: on success it refreshes the library so the results reflect
     * the new tags immediately; on a permission denial it stashes the action to retry.
     * [onSuccess] runs after a successful write (e.g. invalidating cached cover art).
     */
    private fun performWrite(
        affectedIds: List<Long>,
        onSuccess: (suspend () -> Unit)? = null,
        write: suspend () -> TagWriteResult,
    ) {
        viewModelScope.launch {
            when (val result = write()) {
                is TagWriteResult.Success -> {
                    container.refreshLibrary()
                    // Keep listening history in sync with the new tags.
                    val updated = container.musicRepository.songsByIds(affectedIds).first()
                    if (updated.isNotEmpty()) container.propagateRetagToHistory(updated)
                    onSuccess?.invoke()
                    _events.send(LibraryEvent.Message(R.string.edit_tags_saved))
                    _dialog.value = null
                    clearSelection()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingWrite = write
                    pendingWriteIds = affectedIds
                    pendingPostSuccess = onSuccess
                    _events.send(LibraryEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> {
                    _events.send(LibraryEvent.Message(R.string.edit_tags_failed))
                }
            }
        }
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
