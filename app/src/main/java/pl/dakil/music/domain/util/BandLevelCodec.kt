package pl.dakil.music.domain.util

/**
 * Serializes equalizer band gains (millibels) to/from the compact comma-separated
 * string stored in the audio-effects DataStore. Android-free so it can be unit-tested.
 */
object BandLevelCodec {

    fun serialize(levels: List<Int>): String = levels.joinToString(",")

    /** Parses stored levels; a blank or malformed value degrades to an empty (flat) list. */
    fun parse(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").map { it.trim().toIntOrNull() ?: return emptyList() }
    }
}
