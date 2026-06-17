package pl.dakil.music.presentation.components

import pl.dakil.music.domain.model.Song

/** A song paired with a stable per-instance key that survives reordering. */
data class SongEntry(val key: String, val song: Song)

/**
 * Builds stable reorder keys for a list of songs, disambiguating duplicate song ids
 * by occurrence. Shared by the Now Playing queue and the playlist editor so both get
 * identical drag-key behavior.
 */
fun List<Song>.toSongEntries(): List<SongEntry> {
    val seen = HashMap<Long, Int>()
    return map { song ->
        val occurrence = seen.merge(song.id, 1, Int::plus)
        SongEntry("${song.id}#$occurrence", song)
    }
}
