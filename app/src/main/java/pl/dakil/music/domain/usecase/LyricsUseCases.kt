package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LyricsSource
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.LyricsAlignmentRepository
import pl.dakil.music.domain.repository.LyricsRepository
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult
import pl.dakil.music.domain.util.ArtistSplitter
import pl.dakil.music.domain.util.ContentKey
import pl.dakil.music.domain.util.LrcParser
import kotlin.math.abs

/** Lyrics chosen for a song plus the raw lrclib hits (cached for the picker). */
data class LyricsResult(
    val lyrics: Lyrics,
    val lrclibMatches: List<LrclibMatch>,
)

private val EMPTY_LYRICS = Lyrics(emptyList(), synced = false, plainText = "", source = LyricsSource.NONE)

/**
 * Resolves lyrics for a song in priority order: embedded synced → embedded plain →
 * (when [allowLrclib]) the closest-duration lrclib match. Always returns a result;
 * its [LyricsResult.lyrics] has source [LyricsSource.NONE] when nothing was found.
 */
class GetLyricsForSongUseCase(private val lyricsRepository: LyricsRepository) {

    suspend operator fun invoke(song: Song, allowLrclib: Boolean): LyricsResult {
        lyricsRepository.readFromMetadata(song)?.takeIf { it.lines.isNotEmpty() }
            ?.let { return LyricsResult(it, emptyList()) }

        if (!allowLrclib) return LyricsResult(EMPTY_LYRICS, emptyList())

        val artist = ArtistSplitter.split(song.rawArtist).firstOrNull()
            ?: song.artists.firstOrNull().orEmpty()
        val matches = lyricsRepository.searchLrclib(artist, song.title)
        val lyrics = pickBest(matches, song.durationMs)?.let(::lyricsFrom) ?: EMPTY_LYRICS
        return LyricsResult(lyrics, matches)
    }

    companion object {
        /** The match whose duration is closest to [durationMs] among those that have lyrics. */
        fun pickBest(matches: List<LrclibMatch>, durationMs: Long): LrclibMatch? {
            val target = durationMs / 1000.0
            return matches
                .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                .minByOrNull { abs(it.durationSec - target) }
        }

        /** Builds [Lyrics] from a match, preferring its synced lyrics. */
        fun lyricsFrom(match: LrclibMatch): Lyrics {
            match.syncedLyrics?.takeIf { it.isNotBlank() }
                ?.let { return LrcParser.build(it, LyricsSource.LRCLIB) }
            return LrcParser.build(match.plainLyrics.orEmpty(), LyricsSource.LRCLIB)
        }
    }
}

/** Reads embedded lyrics only (no network); null when the file has none. */
class ReadMetadataLyricsUseCase(private val lyricsRepository: LyricsRepository) {
    suspend operator fun invoke(song: Song): Lyrics? =
        lyricsRepository.readFromMetadata(song)?.takeIf { it.lines.isNotEmpty() }
}

/** Manual lrclib search (from the picker dialog). */
class SearchLrclibUseCase(private val lyricsRepository: LyricsRepository) {
    suspend operator fun invoke(artist: String, track: String): List<LrclibMatch> =
        lyricsRepository.searchLrclib(artist, track)
}

/**
 * Writes [lyrics] into the song's metadata, baking in the current [offsetMs] for
 * synced lyrics, then propagates the retag to history and clears the saved offset.
 */
class BurnLyricsToMetadataUseCase(
    private val editTags: EditTagsUseCase,
    private val propagateRetagToHistory: PropagateRetagToHistoryUseCase,
    private val alignmentRepository: LyricsAlignmentRepository,
) {
    suspend operator fun invoke(song: Song, lyrics: Lyrics, offsetMs: Long): TagWriteResult {
        val edit = if (lyrics.synced) {
            TagEdit(
                syncedLyrics = LrcParser.serialize(lyrics.lines, offsetMs),
                plainLyrics = lyrics.lines.joinToString("\n") { it.text },
            )
        } else {
            TagEdit(plainLyrics = lyrics.plainText)
        }
        val result = editTags(listOf(song), edit)
        if (result is TagWriteResult.Success) {
            propagateRetagToHistory(listOf(song))
            alignmentRepository.clear(ContentKey.of(song))
        }
        return result
    }
}
