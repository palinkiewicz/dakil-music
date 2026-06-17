package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import pl.dakil.music.domain.util.AlbumKey
import org.junit.Test

class AlbumKeyTest {

    @Test
    fun equalKeys_ignoreCaseAndWhitespace() {
        val a = AlbumKey.of("Greatest Hits", "The Band")
        val b = AlbumKey.of("  greatest   hits ", "THE BAND")
        assertEquals(a, b)
    }

    @Test
    fun differentTitles_produceDifferentKeys() {
        assertNotEquals(AlbumKey.of("Album A", "Artist"), AlbumKey.of("Album B", "Artist"))
    }

    @Test
    fun differentArtists_produceDifferentKeys() {
        assertNotEquals(AlbumKey.of("Album", "Artist One"), AlbumKey.of("Album", "Artist Two"))
    }
}
