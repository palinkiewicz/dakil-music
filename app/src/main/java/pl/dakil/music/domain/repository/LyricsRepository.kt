package pl.dakil.music.domain.repository

import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.Song

/** Reads lyrics from a song's embedded metadata and from the lrclib.net API. */
interface LyricsRepository {

    /**
     * Reads lyrics embedded in the file's metadata via JAudiotagger, or null when
     * the file has no lyrics tag. Synced vs plain is auto-detected.
     */
    suspend fun readFromMetadata(song: Song): Lyrics?

    /** Searches lrclib.net; returns an empty list on any failure. */
    suspend fun searchLrclib(artist: String, track: String): List<LrclibMatch>
}
