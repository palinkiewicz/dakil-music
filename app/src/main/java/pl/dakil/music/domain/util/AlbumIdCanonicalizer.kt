package pl.dakil.music.domain.util

/**
 * Repairs the *second* half of the damage [MojibakeRepair] undoes.
 *
 * MediaStore derives a song's `album_id` from the album name it indexed, so an album
 * the extractor mangled on some of its tracks but not others ends up with two ids —
 * one per spelling. Fixing the displayed text alone leaves the app grouping those
 * tracks as two albums, because every album surface (browse tree, navigation routes,
 * `Song.albumId` filtering, listening history) is keyed by that id. This maps the
 * mangled spelling's id onto the correct one so the halves reunite.
 *
 * Merging is deliberately one-directional: a *damaged* id is folded into a *clean*
 * one, and two clean ids are never merged with each other. On a device where
 * MediaStore keys albums by artist as well as title, two same-titled albums by
 * different artists legitimately hold different ids — this must not collapse them.
 * The only ids it touches are ones whose album text is provably mojibake.
 */
object AlbumIdCanonicalizer {

    /**
     * Maps mangled album ids onto the id the app should use instead. [songAlbums]
     * pairs each song's album id with its **raw** MediaStore album text — raw because
     * which spelling was damaged is exactly what decides the merge target. Ids that
     * need no change are absent, so an undamaged library yields an empty map.
     */
    fun canonicalIds(songAlbums: List<Pair<Long, String>>): Map<Long, Long> {
        val byTitle = HashMap<String, Grouping>()
        for ((albumId, raw) in songAlbums) {
            if (raw.isBlank()) continue
            val repaired = MojibakeRepair.repair(raw)
            val grouping = byTitle.getOrPut(ContentKey.normalize(repaired)) { Grouping() }
            if (repaired == raw) grouping.cleanIds += albumId else grouping.damagedIds += albumId
        }

        val result = HashMap<Long, Long>()
        for (grouping in byTitle.values) {
            if (grouping.damagedIds.isEmpty()) continue
            val canonical = when (grouping.cleanIds.size) {
                // Every spelling of this album is mangled; there is no "correct" id to
                // prefer, so collapse onto the lowest for a deterministic result.
                0 -> grouping.damagedIds.min()
                // The ordinary case: fold the mangled tracks into the intact album.
                1 -> grouping.cleanIds.first()
                // Two clean ids already share this title, so the device distinguishes
                // albums by more than their name and there is no unambiguous target.
                // Leave the mangled tracks where they are rather than guess.
                else -> continue
            }
            for (id in grouping.damagedIds) {
                if (id != canonical) result[id] = canonical
            }
        }
        return result
    }

    private class Grouping {
        val cleanIds = LinkedHashSet<Long>()
        val damagedIds = LinkedHashSet<Long>()
    }
}
