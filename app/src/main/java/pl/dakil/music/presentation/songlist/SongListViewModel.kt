package pl.dakil.music.presentation.songlist

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
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
}

/** One-shot effects the screen consumes (snackbars, Scoped-Storage consent). */
sealed interface SongListEvent {
    data class Message(@param:StringRes val res: Int) : SongListEvent
    data class RequestWritePermission(val intentSender: android.content.IntentSender) : SongListEvent
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

    private val _dialog = MutableStateFlow<SongDialog?>(null)
    val dialog: StateFlow<SongDialog?> = _dialog.asStateFlow()

    private val _events = Channel<SongListEvent>(Channel.BUFFERED)
    val events: Flow<SongListEvent> = _events.receiveAsFlow()

    /** A tag-write retried verbatim after the user grants Scoped-Storage consent. */
    private var pendingWrite: (suspend () -> TagWriteResult)? = null

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
        performWrite { container.editTags(songs, edit) }
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
        performWrite { container.editTags(edits) }
    }

    /** Called by the screen after the user grants Scoped-Storage write consent. */
    fun onWritePermissionGranted() {
        val retry = pendingWrite ?: return
        pendingWrite = null
        performWrite(retry)
    }

    /**
     * Shared write pipeline: on success it refreshes the library so the list reflects
     * the new tags immediately; on a permission denial it stashes the action to retry.
     */
    private fun performWrite(write: suspend () -> TagWriteResult) {
        viewModelScope.launch {
            when (val result = write()) {
                is TagWriteResult.Success -> {
                    container.refreshLibrary()
                    _events.send(SongListEvent.Message(R.string.edit_tags_saved))
                    _dialog.value = null
                    clearSelection()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingWrite = write
                    _events.send(SongListEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_failed))
                }
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
