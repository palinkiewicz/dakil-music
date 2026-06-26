package pl.dakil.music.presentation.lyrics

import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.data.playback.LyricsStatus
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.LyricLine
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LyricsSource
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.domain.util.ArtistSplitter
import pl.dakil.music.domain.util.ContentKey
import pl.dakil.music.domain.util.LrcParser

data class LyricsScreenState(
    val visible: Boolean = false,
    val status: LyricsStatus = LyricsStatus.SEARCHING,
    val song: Song? = null,
    val synced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val activeLineIndex: Int = -1,
    val offsetMs: Long = 0L,
    val source: LyricsSource = LyricsSource.NONE,
    val matches: List<LrclibMatch> = emptyList(),
    val lrclibEnabled: Boolean = true,
    val canBurn: Boolean = false,
    val defaultArtist: String = "",
    val defaultTrack: String = "",
)

sealed interface LyricsEvent {
    data class RequestWritePermission(val intentSender: IntentSender) : LyricsEvent
    data class Message(@StringRes val resId: Int) : LyricsEvent
}

class LyricsViewModel(private val container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val offsetFlow = container.lyricsController.state
        .map { it.song }
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .flatMapLatest { song ->
            if (song == null) flowOf(0L)
            else container.lyricsAlignmentRepository.offsetMs(ContentKey.of(song))
        }

    val state: StateFlow<LyricsScreenState> = combine(
        container.lyricsController.state,
        container.observePlayback(),
        offsetFlow,
        container.observeSettings(),
    ) { lyricsState, playback, offset, settings ->
        val lyrics = lyricsState.lyrics
        val synced = lyrics?.synced == true
        val song = lyricsState.song
        LyricsScreenState(
            visible = lyricsState.visible,
            status = lyricsState.status,
            song = song,
            synced = synced,
            lines = lyrics?.lines.orEmpty(),
            activeLineIndex = if (synced) {
                LrcParser.activeIndex(lyrics!!.lines, playback.positionMs - offset)
            } else {
                -1
            },
            offsetMs = offset,
            source = lyrics?.source ?: LyricsSource.NONE,
            matches = lyricsState.matches,
            lrclibEnabled = settings.fetchMissingLyricsFromLrclib,
            canBurn = lyrics?.source == LyricsSource.LRCLIB || offset != 0L,
            defaultArtist = song?.let {
                ArtistSplitter.split(it.rawArtist).firstOrNull() ?: it.artists.firstOrNull().orEmpty()
            }.orEmpty(),
            defaultTrack = song?.title.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsScreenState())

    private val _events = Channel<LyricsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pendingBurn: (suspend () -> TagWriteResult)? = null

    /** Seeks to a tapped synced line (its display time = stored time + offset). */
    fun onSeekToLine(line: LyricLine) {
        val time = line.timeMs ?: return
        container.playbackControl.seekTo((time + state.value.offsetMs).coerceAtLeast(0L))
    }

    fun setOffset(offsetMs: Long) {
        val song = state.value.song ?: return
        viewModelScope.launch {
            container.lyricsAlignmentRepository.setOffsetMs(ContentKey.of(song), offsetMs)
        }
    }

    fun nudgeOffset(deltaMs: Long) = setOffset(state.value.offsetMs + deltaMs)

    fun search(artist: String, track: String) = container.lyricsController.search(artist, track)

    fun selectMatch(match: LrclibMatch) = container.lyricsController.selectMatch(match)

    fun burn() {
        val current = state.value
        val song = current.song ?: return
        val lyrics: Lyrics = container.lyricsController.state.value.lyrics ?: return
        performBurn { container.burnLyricsToMetadata(song, lyrics, current.offsetMs) }
    }

    fun retryPendingBurn() {
        val write = pendingBurn ?: return
        pendingBurn = null
        performBurn(write)
    }

    private fun performBurn(write: suspend () -> TagWriteResult) {
        viewModelScope.launch {
            when (val result = write()) {
                is TagWriteResult.Success -> {
                    container.refreshLibrary()
                    container.lyricsController.refresh()
                    _events.send(LyricsEvent.Message(R.string.lyrics_burn_success))
                }

                is TagWriteResult.RequiresPermission -> {
                    pendingBurn = write
                    _events.send(LyricsEvent.RequestWritePermission(result.intentSender))
                }

                is TagWriteResult.Error -> _events.send(LyricsEvent.Message(R.string.lyrics_burn_failed))
            }
        }
    }
}
