package pl.dakil.music.presentation.songlist

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AlbumRule
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.ArtworkData
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.domain.util.AlbumKey
import pl.dakil.music.domain.util.ArtistSplitter
import pl.dakil.music.domain.util.DecomposeOptions
import pl.dakil.music.domain.util.TitleDecomposer
import pl.dakil.music.presentation.navigation.Routes
import pl.dakil.music.presentation.navigation.SongListSource
import pl.dakil.music.presentation.navigation.SourceType

data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val selectedIds: Set<Long> = emptySet(),
    val currentSongId: Long? = null,
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    fun isSelected(id: Long) = id in selectedIds
    fun isFavorite(id: Long) = id in favoriteIds
    fun isCurrent(id: Long) = currentSongId != null && id == currentSongId

    /** True when every selected song is already a favorite (drives add vs remove). */
    val allSelectedAreFavorite: Boolean
        get() = selectedIds.isNotEmpty() && selectedIds.all { it in favoriteIds }
}

/** Which modal dialog (if any) is currently shown over the list. */
sealed interface SongDialog {
    data class EditTags(val songs: List<Song>) : SongDialog {
        val isMulti: Boolean get() = songs.size > 1
    }

    /** Decomposition applies to every song; the dialog previews [songs].first(). */
    data class Decompose(val songs: List<Song>) : SongDialog

    data class AddToPlaylist(val songs: List<Song>) : SongDialog

    /** Search the library for a song to add to the current playlist. */
    data object AddSongToPlaylist : SongDialog

    /** Edit album-wide tags and the album's custom cover-art/author rule. */
    data class EditAlbum(
        val album: Album,
        val rule: AlbumRule?,
        val globalCoverArtMode: AlbumCoverArtMode,
        val globalAuthorMode: AlbumAuthorMode,
    ) : SongDialog

    /** Rename a performer across every song they appear on. */
    data class EditArtist(val name: String) : SongDialog

    /** Rename the current user playlist. */
    data class EditPlaylist(val playlistId: String, val currentName: String) : SongDialog

    /** Confirm deleting the current user playlist (not reversible). */
    data class ConfirmRemovePlaylist(val name: String) : SongDialog

    /** Confirm before retagging [count] songs with album-wide tag changes. */
    data class ConfirmAlbumRetag(val edit: TagEdit, val songs: List<Song>, val count: Int) : SongDialog

    /** Confirm before retagging [count] songs after an artist rename. */
    data class ConfirmArtistRetag(val edits: List<SongTagEdit>, val count: Int) : SongDialog

    /** After picking album art, choose whether to apply it to all songs or just the first. */
    data class AlbumCoverArtTarget(val artwork: ArtworkData) : SongDialog
}

/** One-shot effects the screen consumes (snackbars, Scoped-Storage consent). */
sealed interface SongListEvent {
    data class Message(@param:StringRes val res: Int) : SongListEvent
    data class RequestWritePermission(val intentSender: android.content.IntentSender) : SongListEvent

    /** The viewed source no longer exists (e.g. playlist removed) — leave the screen. */
    data object NavigateBack : SongListEvent
}

class SongListViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val source: SongListSource = parseSource(savedStateHandle)

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    // Only the *which song* matters here, not its position — guard against churn.
    private val currentSongId = container.observePlayback()
        .map { it.currentSong?.id }
        .distinctUntilChanged()

    val uiState: StateFlow<SongListUiState> = combine(
        songsFlow(source),
        container.observeFavorites(),
        selectedIds,
        currentSongId,
    ) { songs, favorites, selected, currentId ->
        // Drop selections for songs no longer present (e.g. after a refresh).
        val validSelection = selected intersect songs.mapTo(HashSet()) { it.id }
        SongListUiState(
            songs = songs,
            favoriteIds = favorites,
            selectedIds = validSelection,
            currentSongId = currentId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongListUiState())

    val userPlaylists: StateFlow<List<UserPlaylist>> = container.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every library song, for the "add song to playlist" search dialog. */
    val allSongs: StateFlow<List<Song>> = container.musicRepository.annotatedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** The album being viewed (album sources only), with author/year resolved. */
    val currentAlbum: StateFlow<Album?> = when (source) {
        is SongListSource.AlbumSource -> container.getAlbums()
            .map { albums -> albums.firstOrNull { it.id == source.albumId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        else -> MutableStateFlow(null)
    }

    /** Albums this performer authored (performer sources only), newest year first. */
    val authoredAlbums: StateFlow<List<Album>> = when (source) {
        is SongListSource.PerformerSource -> container.getAlbums()
            .map { albums ->
                albums.filter { album ->
                    album.authors.any { it.equals(source.name, ignoreCase = true) }
                }.sortedByDescending { it.year }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        else -> MutableStateFlow(emptyList())
    }

    private val _dialog = MutableStateFlow<SongDialog?>(null)
    val dialog: StateFlow<SongDialog?> = _dialog.asStateFlow()

    /** Bumped after a cover-art write so the UI re-fetches embedded art (busts Coil's cache). */
    private val _coverArtVersion = MutableStateFlow(0)
    val coverArtVersion: StateFlow<Int> = _coverArtVersion.asStateFlow()

    private val _events = Channel<SongListEvent>(Channel.BUFFERED)
    val events: Flow<SongListEvent> = _events.receiveAsFlow()

    /** A tag-write retried verbatim after the user grants Scoped-Storage consent. */
    private var pendingWrite: (suspend () -> TagWriteResult)? = null
    private var pendingWriteIds: List<Long> = emptyList()
    private var pendingPostSuccess: (suspend () -> Unit)? = null

    // --- List interaction ----------------------------------------------------------

    fun onSongClick(index: Int) {
        val state = uiState.value
        val song = state.songs.getOrNull(index) ?: return
        if (state.inSelectionMode) {
            toggleSelection(song.id)
        } else {
            container.playSongs(state.songs, index)
        }
    }

    fun onSongLongClick(songId: Long) = toggleSelection(songId)

    private fun toggleSelection(songId: Long) {
        selectedIds.update { current ->
            if (songId in current) current - songId else current + songId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        selectedIds.value = uiState.value.songs.mapTo(HashSet()) { it.id }
    }

    // --- Playback ------------------------------------------------------------------

    /** Plays the whole list in order. */
    fun playAll() = container.playSongs(uiState.value.songs, 0)

    /** Plays the list in a shuffled order. */
    fun shufflePlay() = container.shufflePlay(uiState.value.songs)

    fun addSelectionToQueue() {
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        container.addToQueue(songs)
        viewModelScope.launch { _events.send(SongListEvent.Message(R.string.snackbar_added_to_queue)) }
        clearSelection()
    }

    // --- Playlists -----------------------------------------------------------------

    fun startAddToPlaylist() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = SongDialog.AddToPlaylist(songs)
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
        _events.send(SongListEvent.Message(R.string.snackbar_added_to_playlist))
        _dialog.value = null
        clearSelection()
    }

    // --- Bulk favorites ------------------------------------------------------------

    /**
     * Adds the selection to favorites, or — when every selected song is already a
     * favorite — removes them all instead.
     */
    fun toggleFavoritesForSelection() {
        val state = uiState.value
        val ids = state.selectedIds
        if (ids.isEmpty()) return
        val makeFavorite = !state.allSelectedAreFavorite
        viewModelScope.launch {
            container.setFavorites(ids, favorite = makeFavorite)
            _events.send(
                SongListEvent.Message(
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

    // --- Tag editing & decomposition -----------------------------------------------

    fun startEditTags() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = SongDialog.EditTags(songs)
    }

    fun startDecompose() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = SongDialog.Decompose(songs)
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

    /** Called by the screen after the user grants Scoped-Storage write consent. */
    fun onWritePermissionGranted() {
        val retry = pendingWrite ?: return
        val post = pendingPostSuccess
        pendingWrite = null
        pendingPostSuccess = null
        performWrite(pendingWriteIds, post, retry)
    }

    /**
     * Shared write pipeline: on success it refreshes the library so the list reflects
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
                    _events.send(SongListEvent.Message(R.string.edit_tags_saved))
                    _dialog.value = null
                    clearSelection()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingWrite = write
                    pendingWriteIds = affectedIds
                    pendingPostSuccess = onSuccess
                    _events.send(SongListEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_failed))
                }
            }
        }
    }

    // --- Top-bar actions -----------------------------------------------------------

    /** Appends the whole list to the end of the play queue. */
    fun addAllToQueue() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        container.addToQueue(songs)
        viewModelScope.launch { _events.send(SongListEvent.Message(R.string.snackbar_added_to_queue)) }
    }

    fun startAddSongToPlaylist() {
        _dialog.value = SongDialog.AddSongToPlaylist
    }

    /** Adds a searched song to the current playlist (user playlist) or to favorites. */
    fun addSongToCurrentPlaylist(song: Song) {
        viewModelScope.launch {
            when (val s = source) {
                is SongListSource.UserPlaylistSource ->
                    container.addSongsToPlaylist(s.playlistId, listOf(song.id))
                is SongListSource.PlaylistSource ->
                    if (s.playlist == SystemPlaylist.FAVORITES) {
                        container.setFavorites(setOf(song.id), favorite = true)
                    }
                else -> Unit
            }
            _events.send(SongListEvent.Message(R.string.snackbar_added_to_playlist))
            _dialog.value = null
        }
    }

    // --- Album / artist / playlist editing -----------------------------------------

    fun openAlbumEdit() {
        val album = currentAlbum.value ?: return
        viewModelScope.launch {
            val rule = container.observeAlbumRules().first()
                .firstOrNull { it.albumKey == AlbumKey.of(uiState.value.songs) }
            val s = settings.value
            _dialog.value = SongDialog.EditAlbum(album, rule, s.albumCoverArtMode, s.albumAuthorMode)
        }
    }

    fun openArtistEdit() {
        (source as? SongListSource.PerformerSource)?.let { _dialog.value = SongDialog.EditArtist(it.name) }
    }

    fun openPlaylistEdit() {
        val s = source as? SongListSource.UserPlaylistSource ?: return
        val name = userPlaylists.value.firstOrNull { it.id == s.playlistId }?.name ?: return
        _dialog.value = SongDialog.EditPlaylist(s.playlistId, name)
    }

    /**
     * Persists the album's custom rule (or clears it) and, if the title/year changed,
     * advances to a confirmation before retagging every song in the album.
     */
    fun saveAlbumEdit(
        title: String,
        year: String,
        useCustomSettings: Boolean,
        coverArtMode: AlbumCoverArtMode,
        authorMode: AlbumAuthorMode,
    ) {
        val songs = uiState.value.songs
        val album = currentAlbum.value
        val albumKey = AlbumKey.of(songs)
        viewModelScope.launch {
            if (useCustomSettings) {
                container.upsertAlbumRule(AlbumRule(albumKey, coverArtMode, authorMode))
            } else {
                container.deleteAlbumRule(albumKey)
            }
            val currentYear = album?.year?.takeIf { it > 0 }?.toString().orEmpty()
            val newTitle = title.trim().takeIf { it.isNotBlank() && it != album?.title }
            val newYear = year.trim().takeIf { it.isNotEmpty() && it != currentYear }
            val edit = TagEdit(album = newTitle, year = newYear)
            if (edit.isEmpty || songs.isEmpty()) {
                _dialog.value = null
            } else {
                _dialog.value = SongDialog.ConfirmAlbumRetag(edit, songs, songs.size)
            }
        }
    }

    fun confirmAlbumRetag(edit: TagEdit, songs: List<Song>) {
        performWrite(songs.map { it.id }) { container.editTags(songs, edit) }
    }

    /** Renames a performer by replacing the matching artist token in each song. */
    fun saveArtistName(newName: String) {
        val old = (source as? SongListSource.PerformerSource)?.name ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.equals(old, ignoreCase = true)) {
            _dialog.value = null
            return
        }
        val edits = uiState.value.songs.mapNotNull { song ->
            val replaced = ArtistSplitter.split(song.rawArtist)
                .map { if (it.equals(old, ignoreCase = true)) trimmed else it }
            if (replaced == ArtistSplitter.split(song.rawArtist)) {
                null
            } else {
                SongTagEdit(song, TagEdit(artist = replaced.joinToString(", ")))
            }
        }
        if (edits.isEmpty()) {
            _dialog.value = null
        } else {
            _dialog.value = SongDialog.ConfirmArtistRetag(edits, edits.size)
        }
    }

    fun confirmArtistRetag(edits: List<SongTagEdit>) {
        performWrite(edits.map { it.song.id }) { container.editTags(edits) }
    }

    fun savePlaylistName(name: String) {
        val s = source as? SongListSource.UserPlaylistSource ?: return
        val trimmed = name.trim()
        viewModelScope.launch {
            if (trimmed.isNotEmpty()) container.renamePlaylist(s.playlistId, trimmed)
            _dialog.value = null
        }
    }

    fun promptRemovePlaylist() {
        val s = source as? SongListSource.UserPlaylistSource ?: return
        val name = userPlaylists.value.firstOrNull { it.id == s.playlistId }?.name.orEmpty()
        _dialog.value = SongDialog.ConfirmRemovePlaylist(name)
    }

    fun removeCurrentPlaylist() {
        val s = source as? SongListSource.UserPlaylistSource ?: return
        viewModelScope.launch {
            container.deletePlaylist(s.playlistId)
            _dialog.value = null
            _events.send(SongListEvent.NavigateBack)
        }
    }

    // --- Cover art -----------------------------------------------------------------

    /**
     * Validates a picked image and either applies it to the selection ([forAlbum] =
     * false) or asks whether to apply it to the whole album or just its first song.
     */
    fun onCoverArtPicked(resolver: ContentResolver, uri: Uri, forAlbum: Boolean) {
        viewModelScope.launch {
            val artwork = readArtwork(resolver, uri)
            if (artwork == null) {
                _events.send(SongListEvent.Message(R.string.cover_art_invalid))
                return@launch
            }
            if (forAlbum) {
                _dialog.value = SongDialog.AlbumCoverArtTarget(artwork)
            } else {
                val songs = selectedSongs()
                if (songs.isNotEmpty()) writeArtwork(songs, artwork)
            }
        }
    }

    fun applyAlbumCoverArt(artwork: ArtworkData, toAllSongs: Boolean) {
        val songs = uiState.value.songs
        val targets = if (toAllSongs) songs else listOfNotNull(songs.firstOrNull())
        if (targets.isNotEmpty()) writeArtwork(targets, artwork)
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

    // --- Playlist membership & order -----------------------------------------------

    fun removeSelectionFromPlaylist() {
        val s = source as? SongListSource.UserPlaylistSource ?: return
        val ids = uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.removeSongsFromPlaylist(s.playlistId, ids.toList())
            _events.send(SongListEvent.Message(R.string.snackbar_removed_from_playlist))
            clearSelection()
        }
    }

    fun movePlaylistSong(from: Int, to: Int) {
        viewModelScope.launch {
            when (val s = source) {
                is SongListSource.UserPlaylistSource -> container.reorderPlaylist(s.playlistId, from, to)
                is SongListSource.PlaylistSource ->
                    if (s.playlist == SystemPlaylist.FAVORITES) container.reorderFavorites(from, to)
                else -> Unit
            }
        }
    }

    private fun selectedSongs(): List<Song> {
        val state = uiState.value
        return state.songs.filter { it.id in state.selectedIds }
    }

    // --- Source resolution ---------------------------------------------------------

    private fun songsFlow(source: SongListSource): Flow<List<Song>> = when (source) {
        is SongListSource.AlbumSource -> container.getSongsForAlbum(source.albumId)
        is SongListSource.PerformerSource -> container.getSongsForPerformer(source.name)
        is SongListSource.PlaylistSource -> container.getSongsForPlaylist(source.playlist)
        is SongListSource.UserPlaylistSource -> container.getUserPlaylistSongs(source.playlistId)
    }

    private fun parseSource(handle: SavedStateHandle): SongListSource {
        val type = handle.get<String>(Routes.ARG_SOURCE_TYPE).orEmpty()
        val arg = handle.get<String>(Routes.ARG_SOURCE_ARG).orEmpty()
        return when (SourceType.valueOf(type)) {
            SourceType.ALBUM -> SongListSource.AlbumSource(arg.toLongOrNull() ?: -1L)
            SourceType.PERFORMER -> SongListSource.PerformerSource(arg)
            // A PLAYLIST arg is either a SystemPlaylist enum name or a user-playlist id.
            SourceType.PLAYLIST -> {
                val system = SystemPlaylist.entries.firstOrNull { it.name == arg }
                if (system != null) {
                    SongListSource.PlaylistSource(system)
                } else {
                    SongListSource.UserPlaylistSource(arg)
                }
            }
        }
    }
}
