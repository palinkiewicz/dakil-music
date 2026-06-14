package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.util.DecomposeOptions
import pl.dakil.music.domain.util.TitleDecomposer

class TitleDecomposerTest {

    @Test
    fun authorsBeforeDashSeparator() {
        val result = TitleDecomposer.decompose(
            "Author 1, Author 2 - Song name",
            DecomposeOptions(),
        )
        assertEquals("Song name", result.title)
        assertEquals(listOf("Author 1", "Author 2"), result.artists)
    }

    @Test
    fun extractsFeatFromTitle() {
        val result = TitleDecomposer.decompose(
            "Author 1 - Song name feat. Author 2",
            DecomposeOptions(),
        )
        assertEquals("Song name", result.title)
        assertEquals(listOf("Author 1", "Author 2"), result.artists)
    }

    @Test
    fun stripsParenthesisedFeatAndTrailingNoise() {
        val result = TitleDecomposer.decompose(
            "DJ - Banger (feat. Guest) (Official Video)",
            DecomposeOptions(removeAfter = "(Official"),
        )
        assertEquals("Banger", result.title)
        assertEquals(listOf("DJ", "Guest"), result.artists)
    }

    @Test
    fun authorsAfterSeparator() {
        val result = TitleDecomposer.decompose(
            "Song name | Artist A, Artist B",
            DecomposeOptions(mainSeparator = "|", authorsBeforeSeparator = false),
        )
        assertEquals("Song name", result.title)
        assertEquals(listOf("Artist A", "Artist B"), result.artists)
    }

    @Test
    fun noSeparatorLeavesTitleButStillExtractsFeat() {
        val result = TitleDecomposer.decompose(
            "Just A Song ft. Someone",
            DecomposeOptions(),
        )
        assertEquals("Just A Song", result.title)
        assertEquals(listOf("Someone"), result.artists)
    }
}
