package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.util.BandLevelCodec

class BandLevelCodecTest {

    @Test
    fun roundTrip_preservesLevels() {
        val levels = listOf(-1500, -300, 0, 600, 1500)
        assertEquals(levels, BandLevelCodec.parse(BandLevelCodec.serialize(levels)))
    }

    @Test
    fun serialize_emptyList_isEmptyString() {
        assertEquals("", BandLevelCodec.serialize(emptyList()))
    }

    @Test
    fun parse_nullOrBlank_returnsEmpty() {
        assertEquals(emptyList<Int>(), BandLevelCodec.parse(null))
        assertEquals(emptyList<Int>(), BandLevelCodec.parse(""))
        assertEquals(emptyList<Int>(), BandLevelCodec.parse("   "))
    }

    @Test
    fun parse_malformed_degradesToEmpty() {
        assertEquals(emptyList<Int>(), BandLevelCodec.parse("0,abc,300"))
        assertEquals(emptyList<Int>(), BandLevelCodec.parse("1.5,2"))
    }

    @Test
    fun parse_toleratesWhitespace() {
        assertEquals(listOf(0, 300, -300), BandLevelCodec.parse(" 0 , 300 ,-300 "))
    }
}
