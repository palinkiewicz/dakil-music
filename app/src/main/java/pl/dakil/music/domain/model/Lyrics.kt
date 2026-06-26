package pl.dakil.music.domain.model

/** Where a song's lyrics came from (drives which actions the UI offers). */
enum class LyricsSource {
    /** LRC-timestamped lyrics read from the file's metadata. */
    METADATA_SYNCED,

    /** Plain (untimed) lyrics read from the file's metadata. */
    METADATA_PLAIN,

    /** Lyrics fetched from the lrclib.net API. */
    LRCLIB,

    /** No lyrics were found. */
    NONE,
}

/** A single lyric line; [timeMs] is null for plain (untimed) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

/**
 * Parsed lyrics for a song.
 *
 * @param lines time-ordered lines (each may be timed or plain)
 * @param synced true when at least one line carries a timestamp
 * @param plainText newline-joined text with no timestamps (for plain display / burning)
 * @param source where the lyrics were obtained from
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val plainText: String,
    val source: LyricsSource,
)

/** One search hit returned by the lrclib.net search endpoint. */
data class LrclibMatch(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)
