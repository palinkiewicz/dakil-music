package pl.dakil.music.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LyricsSource
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.PlayerRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.usecase.GetLyricsForSongUseCase
import pl.dakil.music.domain.usecase.SearchLrclibUseCase

enum class LyricsStatus { SEARCHING, FOUND, NOT_FOUND }

/**
 * Lyrics for the current song.
 *
 * @param visible false when the "display lyrics" setting is off — no work is done
 * @param matches cached lrclib search hits (kept while the song is current)
 */
data class LyricsState(
    val visible: Boolean = false,
    val status: LyricsStatus = LyricsStatus.SEARCHING,
    val song: Song? = null,
    val lyrics: Lyrics? = null,
    val matches: List<LrclibMatch> = emptyList(),
)

/**
 * Process-scoped lyrics fetcher: watches the player for song changes and, only
 * when lyrics are enabled, resolves lyrics once per song (metadata → lrclib).
 * Deliberately reacts to `currentSong.id` changes (not position) so it never does
 * work while a song merely advances. Mirrors [PlaybackHistoryTracker]'s lifetime.
 */
class LyricsController(
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository,
    private val getLyricsForSong: GetLyricsForSongUseCase,
    private val searchLrclib: SearchLrclibUseCase,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private var allowLrclib: Boolean = true
    private var refreshJob: Job? = null

    init {
        scope.launch {
            combine(
                playerRepository.playbackState
                    .map { it.currentSong }
                    .distinctUntilChanged { a, b -> a?.id == b?.id },
                settingsRepository.settings
                    .map { it.displayLyrics to it.fetchMissingLyricsFromLrclib }
                    .distinctUntilChanged(),
            ) { song, (display, fetch) -> Triple(song, display, fetch) }
                .collectLatest { (song, display, fetch) ->
                    allowLrclib = fetch
                    when {
                        !display -> _state.value = LyricsState(visible = false)
                        song == null -> _state.value = LyricsState(visible = true, status = LyricsStatus.NOT_FOUND)
                        else -> load(song, fetch)
                    }
                }
        }
    }

    private suspend fun load(song: Song, allowLrclib: Boolean) {
        _state.value = LyricsState(visible = true, status = LyricsStatus.SEARCHING, song = song)
        val result = getLyricsForSong(song, allowLrclib)
        val empty = result.lyrics.source == LyricsSource.NONE || result.lyrics.lines.isEmpty()
        _state.value = LyricsState(
            visible = true,
            status = if (empty) LyricsStatus.NOT_FOUND else LyricsStatus.FOUND,
            song = song,
            lyrics = result.lyrics.takeUnless { empty },
            matches = result.lrclibMatches,
        )
    }

    /** Swaps the displayed lyrics to a user-picked lrclib match (no network call). */
    fun selectMatch(match: LrclibMatch) {
        val current = _state.value
        if (!current.visible || current.song == null) return
        val lyrics = GetLyricsForSongUseCase.lyricsFrom(match)
        _state.value = current.copy(
            status = if (lyrics.lines.isEmpty()) LyricsStatus.NOT_FOUND else LyricsStatus.FOUND,
            lyrics = lyrics.takeIf { it.lines.isNotEmpty() },
        )
    }

    /** Runs a manual lrclib search and refreshes the cached [LyricsState.matches]. */
    fun search(artist: String, track: String) {
        scope.launch {
            val matches = searchLrclib(artist, track)
            _state.value = _state.value.copy(matches = matches)
        }
    }

    /** Re-resolves lyrics for the current song (e.g. after burning into metadata). */
    fun refresh() {
        val song = _state.value.song ?: return
        refreshJob?.cancel()
        refreshJob = scope.launch { load(song, allowLrclib) }
    }
}
