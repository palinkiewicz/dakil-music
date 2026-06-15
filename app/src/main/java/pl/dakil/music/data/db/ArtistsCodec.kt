package pl.dakil.music.data.db

import androidx.room.TypeConverter

/**
 * Encodes a song's split artist list into a single compact column value.
 *
 * The unit-separator `` is used as the delimiter: it never occurs in tag
 * text, so it sidesteps the comma / `feat.` ambiguity that
 * [pl.dakil.music.domain.util.ArtistSplitter] deliberately handles, and is far
 * cheaper to store than JSON.
 */
object ArtistsCodec {
    private const val SEP = ""

    fun encode(list: List<String>): String = list.joinToString(SEP)

    fun decode(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEP)
}

/** Room [TypeConverter]s delegating to [ArtistsCodec]. */
class ArtistsConverter {
    @TypeConverter
    fun toDb(list: List<String>): String = ArtistsCodec.encode(list)

    @TypeConverter
    fun fromDb(value: String): List<String> = ArtistsCodec.decode(value)
}
