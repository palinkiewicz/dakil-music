package pl.dakil.music.presentation.history

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.music.R
import pl.dakil.music.data.csv.ListeningHistoryCsv
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.domain.util.DecomposeOptions
import pl.dakil.music.domain.util.TitleDecomposer
import java.io.BufferedReader

data class HistoryUiState(
    val records: List<ListeningRecord> = emptyList(),
    val songsById: Map<Long, Song> = emptyMap(),
    val favoriteIds: Set<Long> = emptySet(),
    val currentSongId: Long? = null,
    val selectedIds: Set<Long> = emptySet(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
) {
    fun liveSong(songId: Long): Song? = songsById[songId]
    fun isPresent(songId: Long): Boolean = songId in songsById
    fun isSelected(songId: Long) = songId in selectedIds
    fun isFavorite(songId: Long) = songId in favoriteIds
    fun isCurrent(songId: Long) = currentSongId != null && songId == currentSongId
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val allSelectedAreFavorite: Boolean
        get() = selectedIds.isNotEmpty() && selectedIds.all { it in favoriteIds }
}

sealed interface HistoryDialog {
    data class EditTags(val songs: List<Song>) : HistoryDialog
    data class Decompose(val songs: List<Song>) : HistoryDialog
    data class AddToPlaylist(val songs: List<Song>) : HistoryDialog

    /** "Merge with existing": pick a live song to fold a deleted record's plays into. */
    data class Merge(val deletedSongId: Long, val label: String) : HistoryDialog
}

sealed interface HistoryEvent {
    data class Message(@param:StringRes val res: Int) : HistoryEvent
    data class ImportResult(val imported: Int, val skipped: Int) : HistoryEvent
    data class RequestWritePermission(val intentSender: android.content.IntentSender) : HistoryEvent
}

class ListeningHistoryViewModel(private val container: AppContainer) : ViewModel() {

    private data class Page(
        val records: List<ListeningRecord> = emptyList(),
        val hasMore: Boolean = false,
        val loading: Boolean = true,
    )

    private val page = MutableStateFlow(Page())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** How many records are currently shown; grows by [PAGE_SIZE] on "show more". */
    private var targetCount = PAGE_SIZE

    private val currentSongId = container.observePlayback()
        .map { it.currentSong?.id }
        .distinctUntilChanged()

    val uiState: StateFlow<HistoryUiState> = combine(
        page,
        container.musicRepository.annotatedSongs,
        container.observeFavorites(),
        currentSongId,
        selectedIds,
    ) { page, songs, favorites, current, selected ->
        val byId = songs.associateBy { it.id }
        // Only present songs can be selected.
        val validSelection = selected.filterTo(HashSet()) { it in byId }
        HistoryUiState(
            records = page.records,
            songsById = byId,
            favoriteIds = favorites,
            currentSongId = current,
            selectedIds = validSelection,
            hasMore = page.hasMore,
            loading = page.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    val userPlaylists = container.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow<HistoryDialog?>(null)
    val dialog: StateFlow<HistoryDialog?> = _dialog.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    private var pendingWrite: (suspend () -> TagWriteResult)? = null
    private var pendingWriteIds: List<Long> = emptyList()

    init {
        reload()
        // Live refresh: the table changes as the active session is checkpointed.
        viewModelScope.launch {
            container.observeHistoryChanges().collect { reload() }
        }
    }

    // --- Paging --------------------------------------------------------------------

    private fun reload() {
        viewModelScope.launch {
            val total = container.getHistoryCount()
            val records = container.getHistoryPage(targetCount, 0)
            page.value = Page(records, hasMore = records.size < total, loading = false)
        }
    }

    fun loadMore() {
        if (page.value.loading || !page.value.hasMore) return
        targetCount += PAGE_SIZE
        reload()
    }

    // --- Selection -----------------------------------------------------------------

    fun onRecordClick(record: ListeningRecord) {
        val state = uiState.value
        val song = state.liveSong(record.songId) ?: return // deleted: not interactive on tap
        if (state.inSelectionMode) toggleSelection(song.id) else container.playSongs(listOf(song), 0)
    }

    fun onRecordLongClick(record: ListeningRecord) {
        val state = uiState.value
        val song = state.liveSong(record.songId)
        if (song != null) {
            toggleSelection(song.id)
        } else {
            _dialog.value = HistoryDialog.Merge(record.songId, record.title)
        }
    }

    private fun toggleSelection(songId: Long) {
        selectedIds.update { if (songId in it) it - songId else it + songId }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        val state = uiState.value
        selectedIds.value = state.records.map { it.songId }.filterTo(HashSet()) { it in state.songsById }
    }

    private fun selectedSongs(): List<Song> {
        val state = uiState.value
        return state.selectedIds.mapNotNull { state.songsById[it] }
    }

    // --- Actions on selection ------------------------------------------------------

    fun addSelectionToQueue() {
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        container.addToQueue(songs)
        viewModelScope.launch { _events.send(HistoryEvent.Message(R.string.snackbar_added_to_queue)) }
        clearSelection()
    }

    fun toggleFavoritesForSelection() {
        val state = uiState.value
        val ids = state.selectedIds
        if (ids.isEmpty()) return
        val makeFavorite = !state.allSelectedAreFavorite
        viewModelScope.launch {
            container.setFavorites(ids, favorite = makeFavorite)
            _events.send(
                HistoryEvent.Message(
                    if (makeFavorite) R.string.snackbar_added_to_favorites
                    else R.string.snackbar_removed_from_favorites,
                ),
            )
            clearSelection()
        }
    }

    fun startAddToPlaylist() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = HistoryDialog.AddToPlaylist(songs)
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
        _events.send(HistoryEvent.Message(R.string.snackbar_added_to_playlist))
        _dialog.value = null
        clearSelection()
    }

    fun startEditTags() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = HistoryDialog.EditTags(songs)
    }

    fun startDecompose() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) _dialog.value = HistoryDialog.Decompose(songs)
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

    fun onWritePermissionGranted() {
        val retry = pendingWrite ?: return
        pendingWrite = null
        performWrite(pendingWriteIds, retry)
    }

    private fun performWrite(affectedIds: List<Long>, write: suspend () -> TagWriteResult) {
        viewModelScope.launch {
            when (val result = write()) {
                is TagWriteResult.Success -> {
                    container.refreshLibrary()
                    val updated = container.musicRepository.songsByIds(affectedIds).first()
                    if (updated.isNotEmpty()) container.propagateRetagToHistory(updated)
                    _events.send(HistoryEvent.Message(R.string.edit_tags_saved))
                    _dialog.value = null
                    clearSelection()
                    reload()
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingWrite = write
                    pendingWriteIds = affectedIds
                    _events.send(HistoryEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> _events.send(HistoryEvent.Message(R.string.edit_tags_failed))
            }
        }
    }

    // --- Merge ---------------------------------------------------------------------

    fun mergeDeletedSong(deletedSongId: Long, target: Song) {
        viewModelScope.launch {
            container.mergeHistory(deletedSongId, target)
            _dialog.value = null
            _events.send(HistoryEvent.Message(R.string.history_merge_done))
            reload()
        }
    }

    // --- CSV import / export -------------------------------------------------------

    fun exportHistory(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val records = container.exportHistory()
                    resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.append(ListeningHistoryCsv.header).append('\n')
                        records.forEach { writer.append(ListeningHistoryCsv.toRow(it)).append('\n') }
                    } ?: return@runCatching false
                    true
                }.getOrDefault(false)
            }
            _events.send(
                HistoryEvent.Message(if (ok) R.string.history_export_done else R.string.history_export_failed),
            )
        }
    }

    fun importHistory(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { parse(it) }
                }.getOrNull()
            }
            if (result == null) {
                _events.send(HistoryEvent.Message(R.string.history_import_invalid))
                return@launch
            }
            container.importHistory(result.records)
            _events.send(HistoryEvent.ImportResult(result.records.size, result.skipped))
            reload()
        }
    }

    private data class ParseResult(val records: List<ListeningRecord>, val skipped: Int)

    /** Returns null when the header is missing/invalid (whole import rejected). */
    private fun parse(reader: BufferedReader): ParseResult? {
        val headerLine = reader.readLine() ?: return null
        if (!ListeningHistoryCsv.isValidHeader(headerLine)) return null
        val records = ArrayList<ListeningRecord>()
        var skipped = 0
        reader.forEachLine { line ->
            if (line.isNotBlank()) {
                val record = ListeningHistoryCsv.parseRow(line)
                if (record != null) records.add(record) else skipped++
            }
        }
        return ParseResult(records, skipped)
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
