package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.Song

/**
 * Derives a stable, normalized identity for a song from its title, artists and
 * album. Used to re-link listening history when MediaStore reassigns ids (a song
 * deleted and re-added gets a new `_ID` but keeps the same content key).
 *
 * Normalization trims, collapses inner whitespace and lowercases (root locale) so
 * cosmetic tag differences don't break a match.
 */
object ContentKey {

    private val WHITESPACE = Regex("\\s+")
    private const val SEP = "|"

    fun of(title: String, artists: List<String>, album: String): String =
        normalize(title) + SEP + normalize(artists.joinToString(",")) + SEP + normalize(album)

    fun of(song: Song): String = of(song.title, song.artists, song.album)

    fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase()
}
