package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.Song

/**
 * Derives a stable, normalized identity for an album from its title and a base
 * artist. Used to key per-album custom rules so they survive MediaStore reassigning
 * the album's numeric id (an album deleted and re-added keeps the same key).
 *
 * Normalization is shared with [ContentKey] (trim, collapse whitespace, lowercase).
 */
object AlbumKey {

    private const val SEP = "|"

    fun of(title: String, baseArtist: String): String =
        ContentKey.normalize(title) + SEP + ContentKey.normalize(baseArtist)

    /**
     * Key for a group of an album's songs. The base artist is taken from the song
     * with the lowest track number (then id) so the key is deterministic regardless
     * of the order the songs are supplied in.
     */
    fun of(albumSongs: List<Song>): String {
        if (albumSongs.isEmpty()) return of("", "")
        val base = albumSongs.minWith(compareBy({ it.trackNumber }, { it.id }))
        return of(albumSongs.first().album, base.rawArtist)
    }
}
