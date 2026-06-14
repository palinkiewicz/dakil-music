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

    /** The song currently open in the tag editor, if any. */
    private val _editingSong = MutableStateFlow<Song?>(null)
    val editingSong: StateFlow<Song?> = _editingSong.asStateFlow()

    private val _events = Channel<SongListEvent>(Channel.BUFFERED)
    val events: Flow<SongListEvent> = _events.receiveAsFlow()

    private var pendingTagWrite: Pair<Song, TagEdit>? = null

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

    // --- Bulk actions --------------------------------------------------------------

    fun addSelectedToFavorites() {
        val ids = uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.setFavorites(ids, favorite = true)
            _events.send(SongListEvent.Message(R.string.snackbar_added_to_favorites))
            clearSelection()
        }
    }

    /** Opens the tag editor for the first selected song (tag writing is per-file). */
    fun startEditTags() {
        val firstId = uiState.value.selectedIds.firstOrNull() ?: return
        _editingSong.value = uiState.value.songs.firstOrNull { it.id == firstId }
    }

    fun dismissEditTags() {
        _editingSong.value = null
    }

    fun saveTags(song: Song, edit: TagEdit) {
        viewModelScope.launch {
            when (val result = container.editTags(song, edit)) {
                is TagWriteResult.Success -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_saved))
                    _editingSong.value = null
                    clearSelection()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingTagWrite = song to edit
                    _events.send(SongListEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> {
                    _events.send(SongListEvent.Message(R.string.edit_tags_permission_needed))
                }
            }
        }
    }

    /** Called by the screen after the user grants Scoped-Storage write consent. */
    fun onWritePermissionGranted() {
        val (song, edit) = pendingTagWrite ?: return
        pendingTagWrite = null
        saveTags(song, edit)
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
