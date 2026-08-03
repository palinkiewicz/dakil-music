package pl.dakil.music.domain.util

/**
 * Collapses tag names that differ only in case into one entry.
 *
 * Tags are typed by hand across releases, so the same artist arrives as both
 * "27.Fuckdemons" and "27.FUCKDEMONS". Every surface that *looks a name up* already
 * matches case-insensitively — `songsForPerformer`, `songsForGenre`, the Android Auto
 * browse tree — so only the surfaces that *list* names disagreed, showing one artist
 * twice with each entry claiming a song count that neither matched the other nor the
 * list of songs it opened.
 *
 * The display spelling is the one used on the most songs, so an occasional shouty
 * credit does not rename an artist; ties fall back to alphabetical order to keep the
 * result deterministic. This matches how `StatisticsAggregator` already groups
 * artists, so the Artists tab and its play counts agree on who is who.
 */
object CanonicalNames {

    /**
     * Counts [names] — one entry per occurrence, so pass an artist once per song it
     * appears on — grouped case-insensitively. Blank names are skipped. Returns each
     * group's display spelling mapped to the size of the whole group; the caller
     * decides the ordering.
     */
    fun counts(names: List<String>): Map<String, Int> {
        // lowercase key -> spelling -> how many entries used that spelling
        val groups = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        for (name in names) {
            if (name.isBlank()) continue
            val spellings = groups.getOrPut(name.lowercase()) { LinkedHashMap() }
            spellings[name] = (spellings[name] ?: 0) + 1
        }

        val result = LinkedHashMap<String, Int>(groups.size)
        for (spellings in groups.values) {
            val display = spellings.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .first().key
            result[display] = spellings.values.sum()
        }
        return result
    }
}
