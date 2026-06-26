package pl.dakil.music.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.PlayerRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.usecase.GetLyricsForSongUseCase
import pl.dakil.music.domain.usecase.ReadMetadataLyricsUseCase
import pl.dakil.music.domain.usecase.SearchLrclibUseCase
import pl.dakil.music.domain.util.ArtistSplitter

enum class LyricsStatus { SEARCHING, FOUND, NOT_FOUND }

/**
 * Lyrics for the current song.
 *
 * @param visible false when the "display lyrics" setting is off — no work is done
 * @param matches cached lrclib search hits (kept while the song is current)
 * @param searching true while a manual lrclib search (picker dialog) is running
 */
data class LyricsState(
    val visible: Boolean = false,
    val status: LyricsStatus = LyricsStatus.SEARCHING,
    val song: Song? = null,
    val lyrics: Lyrics? = null,
    val matches: List<LrclibMatch> = emptyList(),
    val searching: Boolean = false,
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
    private val readMetadataLyrics: ReadMetadataLyricsUseCase,
    private val searchLrclib: SearchLrclibUseCase,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private var allowLrclib: Boolean = true
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var searchJob: Job? = null

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
                .collect { (song, display, fetch) ->
                    allowLrclib = fetch
                    // Cancel (without joining) so a new song's lookup starts immediately
                    // even if the previous one is stuck in a slow/blocking network call.
                    loadJob?.cancel()
                    when {
                        !display -> _state.value = LyricsState(visible = false)
                        song == null -> _state.value = LyricsState(visible = true, status = LyricsStatus.NOT_FOUND)
                        else -> loadJob = launch { load(song, fetch) }
                    }
                }
        }
    }

    /**
     * Resolves lyrics for [song] in two phases. The local metadata read happens
     * silently — keeping the previous card until it completes — so switching to a
     * song with embedded lyrics never flashes the spinner. Only the (slow) lrclib
     * network lookup shows SEARCHING. Cancelling this coroutine (a new song
     * arriving) cancels both the read and the in-flight lookup immediately.
     */
    private suspend fun load(song: Song, allowLrclib: Boolean) = coroutineScope {
        // Phase 1: embedded metadata (no spinner).
        val metadata = readMetadataLyrics(song)
        if (metadata != null) {
            _state.value = LyricsState(visible = true, status = LyricsStatus.FOUND, song = song, lyrics = metadata)
            return@coroutineScope
        }
        if (!allowLrclib) {
            _state.value = LyricsState(visible = true, status = LyricsStatus.NOT_FOUND, song = song)
            return@coroutineScope
        }

        // Phase 2: lrclib network lookup (shows SEARCHING once it's clearly running).
        val indicator = launch {
            delay(SEARCH_INDICATOR_DELAY_MS)
            _state.value = LyricsState(visible = true, status = LyricsStatus.SEARCHING, song = song)
        }
        val artist = ArtistSplitter.split(song.rawArtist).firstOrNull()
            ?: song.artists.firstOrNull().orEmpty()
        val matches = searchLrclib(artist, song.title)
        indicator.cancel()
        val lyrics = GetLyricsForSongUseCase.pickBest(matches, song.durationMs)
            ?.let(GetLyricsForSongUseCase::lyricsFrom)
        val empty = lyrics == null || lyrics.lines.isEmpty()
        _state.value = LyricsState(
            visible = true,
            status = if (empty) LyricsStatus.NOT_FOUND else LyricsStatus.FOUND,
            song = song,
            lyrics = lyrics?.takeUnless { empty },
            matches = matches,
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

    /**
     * Runs a manual lrclib search (picker dialog), toggling [LyricsState.searching]
     * around it so the dialog can show a progress indicator. A new search cancels
     * the previous one.
     */
    fun search(artist: String, track: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            _state.value = _state.value.copy(searching = true)
            val matches = searchLrclib(artist, track)
            _state.value = _state.value.copy(matches = matches, searching = false)
        }
    }

    /** Re-resolves lyrics for the current song (e.g. after burning into metadata). */
    fun refresh() {
        val song = _state.value.song ?: return
        refreshJob?.cancel()
        refreshJob = scope.launch { load(song, allowLrclib) }
    }

    private companion object {
        const val SEARCH_INDICATOR_DELAY_MS = 200L
    }
}
