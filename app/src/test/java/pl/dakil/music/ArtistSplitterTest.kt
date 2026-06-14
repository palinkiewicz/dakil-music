package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.util.ArtistSplitter

class ArtistSplitterTest {

    @Test
    fun splitsOnCommaSemicolonAndFeat() {
        assertEquals(
            listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"),
            ArtistSplitter.split("Daft Punk, Pharrell Williams feat. Nile Rodgers"),
        )
        assertEquals(
            listOf("A", "B"),
            ArtistSplitter.split("A; B"),
        )
    }

    @Test
    fun trimsAndDeduplicatesCaseInsensitively() {
        assertEquals(
            listOf("Queen", "David Bowie"),
            ArtistSplitter.split("Queen feat. David Bowie & queen"),
        )
    }

    @Test
    fun returnsEmptyForBlankOrNull() {
        assertEquals(emptyList<String>(), ArtistSplitter.split(null))
        assertEquals(emptyList<String>(), ArtistSplitter.split("   "))
    }

    @Test
    fun keepsSingleArtistIntact() {
        assertEquals(listOf("Radiohead"), ArtistSplitter.split("Radiohead"))
    }
}
