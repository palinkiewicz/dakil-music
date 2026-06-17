package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.Song

/**
 * Derives an album's author(s) from the artists of its songs, per [AlbumAuthorMode].
 *
 * Pure and deterministic. [authors] returns the ordered list of author names (used
 * both for display and to decide whether an artist "authored" the album); [display]
 * joins them for single-line rendering. The core logic operates on per-song artist
 * lists ([authorsOf]) so it is testable without Android value types.
 */
object AlbumAuthors {

    fun authors(songs: List<Song>, mode: AlbumAuthorMode): List<String> =
        authorsOf(songs.map { it.artists }, mode)

    fun display(songs: List<Song>, mode: AlbumAuthorMode): String =
        authors(songs, mode).joinToString(", ")

    /** Album-author derivation over the artist lists of an album's songs, in track order. */
    fun authorsOf(perSongArtists: List<List<String>>, mode: AlbumAuthorMode): List<String> {
        if (perSongArtists.isEmpty()) return emptyList()
        val first = perSongArtists.first()
        return when (mode) {
            AlbumAuthorMode.FIRST_SONG_ARTISTS -> first
            AlbumAuthorMode.FIRST_ARTIST_OF_FIRST_SONG -> listOfNotNull(first.firstOrNull())
            AlbumAuthorMode.MOST_COMMON -> mostCommon(perSongArtists)
            AlbumAuthorMode.ALL_ARTISTS -> allArtists(perSongArtists)
        }
    }

    /** The single artist appearing on the most songs; ties broken by first appearance. */
    private fun mostCommon(perSongArtists: List<List<String>>): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (artists in perSongArtists) {
            // Count an artist at most once per song.
            for (artist in artists.distinct()) {
                counts[artist] = (counts[artist] ?: 0) + 1
            }
        }
        val top = counts.maxByOrNull { it.value }?.key ?: return emptyList()
        return listOf(top)
    }

    /** Every distinct artist across the album, preserving first-appearance order. */
    private fun allArtists(perSongArtists: List<List<String>>): List<String> {
        val seen = LinkedHashSet<String>()
        for (artists in perSongArtists) seen.addAll(artists)
        return seen.toList()
    }
}
