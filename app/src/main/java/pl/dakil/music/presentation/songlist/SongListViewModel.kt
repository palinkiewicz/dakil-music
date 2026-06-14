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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.presentation.navigation.Routes
import pl.dakil.music.presentation.navigation.SongListSource
import pl.dakil.music.presentation.navigation.SourceType

data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val selectedIds: Set<Long> = emptySet(),
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    fun isSelected(id: Long) = id in selectedIds
    fun isFavorite(id: Long) = id in favoriteIds

    /** True when every selected song is already a favorite (drives add vs remove). */
    val allSelectedAreFavorite: Boolean
        get() = selectedIds.isNotEmpty() && selectedIds.all { it in favoriteIds }
}

/** Which modal dialog (if any) is currently shown over the list. */
sealed interface SongDialog {
    data class EditTags(val songs: List<Song>) : SongDialog {
        val isMulti: Boolean get() = songs.size > 1
    }

    data class Decompose(val song: Song) : SongDialog
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

    val uiState: StateFlow<SongListUiState> = combine(
        songsFlow(source),
        container.observeFavorites(),
        selectedIds,
    ) { songs, favorites, selected ->
        // Drop selections for songs no longer present (e.g. after a refresh).
        val validSelection = selected intersect songs.mapTo(HashSet()) { it.id }
        SongListUiState(songs = songs, favoriteIds = favorites, selectedIds = validSelection)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongListUiState())

    private val _dialog = MutableStateFlow<SongDialog?>(null)
    val dialog: StateFlow<SongDialog?> = _dialog.asStateFlow()

    private val _events = Channel<SongListEvent>(Channel.BUFFERED)
    val events: Flow<SongListEvent> = _events.receiveAsFlow()

    private var pendingTagWrite: Pair<List<Song>, TagEdit>? = null

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

    /** Title decomposition only makes sense for a single track. */
    fun startDecompose() {
        selectedSongs().singleOrNull()?.let { _dialog.value = SongDialog.Decompose(it) }
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun saveTags(songs: List<Song>, edit: TagEdit) {
        if (edit.isEmpty || songs.isEmpty()) {
            dismissDialog()
            return
        }
        viewModelScope.launch {
            when (val result = container.editTags(songs, edit)) {
                is TagWriteResult.Success -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_saved))
                    _dialog.value = null
                    clearSelection()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingTagWrite = songs to edit
                    _events.send(SongListEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_failed))
                }
            }
        }
    }

    /** Applies a decomposition result by writing the cleaned title + extracted artists. */
    fun applyDecomposition(song: Song, title: String, artists: List<String>) {
        saveTags(
            songs = listOf(song),
            edit = TagEdit(
                title = title.takeIf { it.isNotBlank() },
                artist = artists.joinToString(", ").takeIf { it.isNotBlank() },
            ),
        )
    }

    /** Called by the screen after the user grants Scoped-Storage write consent. */
    fun onWritePermissionGranted() {
        val (songs, edit) = pendingTagWrite ?: return
        pendingTagWrite = null
        saveTags(songs, edit)
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
    }

    private fun parseSource(handle: SavedStateHandle): SongListSource {
        val type = handle.get<String>(Routes.ARG_SOURCE_TYPE).orEmpty()
        val arg = handle.get<String>(Routes.ARG_SOURCE_ARG).orEmpty()
        return when (SourceType.valueOf(type)) {
            SourceType.ALBUM -> SongListSource.AlbumSource(arg.toLongOrNull() ?: -1L)
            SourceType.PERFORMER -> SongListSource.PerformerSource(arg)
            SourceType.PLAYLIST -> SongListSource.PlaylistSource(SystemPlaylist.valueOf(arg))
        }
    }
}
