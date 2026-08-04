package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.AlbumAuthorMode

/**
 * Reunites one album that MediaStore indexed under two `album_id`s.
 *
 * MediaStore does not key an album by its title alone. It hashes the title together
 * with the **album artist tag when the file has one, and the file's parent directory
 * when it doesn't** — a fallback meant to keep same-titled albums by different
 * artists apart. Two things routinely defeat it:
 *
 * - the extractor mangles the title on some tracks but not others ([MojibakeRepair]),
 *   so each spelling gets its own id;
 * - only *some* tracks of an album carry an album artist tag, so the tagged ones are
 *   keyed by artist and the rest by directory — two ids for one album, even when
 *   every file sits in the same folder.
 *
 * Fixing the displayed text alone leaves the app showing two albums, because every
 * album surface (browse tree, navigation routes, `Song.albumId` filtering, listening
 * history, cover art) is keyed by that id. This maps the stray ids onto the one the
 * app should use instead.
 *
 * Merging is deliberately conservative, since a wrong merge fuses two real albums:
 *
 * - albums are grouped by title **and author** ([AlbumKey]), so two artists' shared
 *   `Greatest Hits` are never candidates for each other in the first place;
 * - an album's author is the album artist tag if it has one, else the artist
 *   appearing on most of its tracks — so a collab on one track ("A" on the rest,
 *   "A, B" on that one) does not change the album's identity;
 * - a *damaged* spelling folds into a *clean* one, never the reverse;
 * - two clean ids only merge when exactly one of them is album-artist-tagged, which
 *   is the signature of the tagged/untagged split above. Two untagged clean ids
 *   differ only by directory, and that is a distinction the app cannot tell apart
 *   from two genuinely different albums — so it leaves them alone.
 *
 * Whenever the target is ambiguous, nothing in that group moves.
 */
object AlbumIdCanonicalizer {

    /**
     * The album-identifying fields of one song. [rawAlbum] must arrive **raw**, since
     * which spelling MediaStore damaged is exactly what decides the merge; the artist
     * fields are expected already repaired, as everything downstream reads them.
     */
    data class Track(
        val albumId: Long,
        val rawAlbum: String,
        /** The album artist tag, or empty when the file carries none. */
        val albumArtist: String = "",
        /** The song's own artists, already split by [ArtistSplitter]. */
        val artists: List<String> = emptyList(),
    )

    /**
     * Maps stray album ids onto the id the app should use instead. Ids that need no
     * change are absent, so an undamaged, consistently tagged library yields an empty
     * map.
     */
    fun canonicalIds(tracks: List<Track>): Map<Long, Long> {
        val albums = LinkedHashMap<Long, Album>()
        for (track in tracks) {
            if (track.rawAlbum.isBlank()) continue
            albums.getOrPut(track.albumId) { Album(track.albumId) }.add(track)
        }

        val groups = LinkedHashMap<String, MutableList<Album>>()
        for (album in albums.values) {
            groups.getOrPut(album.identity()) { ArrayList() } += album
        }

        val result = HashMap<Long, Long>()
        for (group in groups.values) {
            if (group.size < 2) continue
            val canonical = canonicalOf(group) ?: continue
            for (album in group) {
                if (album.id != canonical.id) result[album.id] = canonical.id
            }
        }
        return result
    }

    /**
     * The id the rest of [group] should fold into, or null when the choice would be a
     * guess — in which case the whole group is left as MediaStore indexed it.
     */
    private fun canonicalOf(group: List<Album>): Album? {
        val clean = group.filter { !it.isDamaged }
        // Every spelling is mangled; there is no intact id to prefer. A single tagged
        // id is still the most stable target, otherwise the lowest keeps it deterministic.
        if (clean.isEmpty()) {
            return group.singleOrNull { it.isTagged } ?: group.minBy { it.id }
        }

        val tagged = clean.filter { it.isTagged }
        return when {
            // The ordinary case: one intact album, with the mangled tracks around it.
            clean.size == 1 -> clean.single()
            // The tagged/untagged split — the tagged id wins, as it is the one that
            // survives the files being moved to another folder.
            tagged.size == 1 -> tagged.single()
            // Several equally plausible targets: two untagged ids that differ only by
            // directory, or two tagged ids the device is separating for a reason we
            // cannot see. Guessing here would fuse two real albums.
            else -> null
        }
    }

    /** One MediaStore `album_id`, accumulated across the songs that carry it. */
    private class Album(val id: Long) {
        private var title = ""
        private var albumArtist = ""
        private val artistsPerSong = ArrayList<List<String>>()

        /** True when the title MediaStore indexed is mojibake, so this id is a stray. */
        var isDamaged = false
            private set

        /** True when at least one of the album's files carries an album artist tag. */
        val isTagged get() = albumArtist.isNotEmpty()

        fun add(track: Track) {
            val repaired = MojibakeRepair.repair(track.rawAlbum)
            if (repaired != track.rawAlbum) isDamaged = true
            if (title.isEmpty()) title = repaired
            if (albumArtist.isEmpty()) albumArtist = track.albumArtist
            artistsPerSong += track.artists
        }

        /**
         * Title plus author, normalized. The author is the album artist tag when the
         * album has one, else the artist on most of its tracks — the same value an
         * untagged album's tagged half would have carried, which is what lets the two
         * halves find each other.
         */
        fun identity(): String = AlbumKey.of(title, author())

        private fun author(): String = albumArtist.ifEmpty {
            AlbumAuthors.authorsOf(artistsPerSong, AlbumAuthorMode.MOST_COMMON)
                .firstOrNull()
                .orEmpty()
        }
    }
}
