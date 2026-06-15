package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pl.dakil.music.domain.util.ContentKey

class ContentKeyTest {

    @Test
    fun normalize_trimsCollapsesAndLowercases() {
        assertEquals("hello world", ContentKey.normalize("  Hello   World  "))
    }

    @Test
    fun equalKeys_ignoreCaseAndWhitespace() {
        val a = ContentKey.of("Song Title", listOf("Artist One"), "Album")
        val b = ContentKey.of("  song   title ", listOf("artist one"), "ALBUM")
        assertEquals(a, b)
    }

    @Test
    fun differentAlbums_produceDifferentKeys() {
        val a = ContentKey.of("Title", listOf("Artist"), "Album A")
        val b = ContentKey.of("Title", listOf("Artist"), "Album B")
        assertNotEquals(a, b)
    }
}
