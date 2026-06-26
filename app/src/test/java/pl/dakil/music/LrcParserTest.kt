package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.domain.model.LyricLine
import pl.dakil.music.domain.util.LrcParser

class LrcParserTest {

    @Test
    fun `detects synced text`() {
        assertTrue(LrcParser.isSynced("[00:03.46]Hello"))
        assertFalse(LrcParser.isSynced("Hello\nWorld"))
    }

    @Test
    fun `parses timestamps to milliseconds`() {
        val lines = LrcParser.parse("[00:03.46]Hello\n[01:05.07]World")
        assertEquals(2, lines.size)
        assertEquals(3460L, lines[0].timeMs)
        assertEquals("Hello", lines[0].text)
        assertEquals(65070L, lines[1].timeMs)
    }

    @Test
    fun `parses two and three digit fractions`() {
        assertEquals(3400L, LrcParser.parse("[00:03.4]x")[0].timeMs)
        assertEquals(3460L, LrcParser.parse("[00:03.46]x")[0].timeMs)
        assertEquals(3460L, LrcParser.parse("[00:03.460]x")[0].timeMs)
        assertEquals(3000L, LrcParser.parse("[00:03]x")[0].timeMs)
    }

    @Test
    fun `expands repeated timestamps on one line`() {
        val lines = LrcParser.parse("[00:01.00][00:10.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals(10000L, lines[1].timeMs)
        assertTrue(lines.all { it.text == "Chorus" })
    }

    @Test
    fun `strips timestamps for plain text`() {
        val plain = LrcParser.stripTimestamps("[00:03.46]Hello\n[01:05.07]World")
        assertEquals("Hello\nWorld", plain)
    }

    @Test
    fun `serializes lines with offset and clamps at zero`() {
        val lines = listOf(LyricLine(1000L, "a"), LyricLine(2000L, "b"))
        assertEquals("[00:01.50]a\n[00:02.50]b", LrcParser.serialize(lines, 500L))
        // Negative offset clamps the first line at 0.
        assertEquals("[00:00.00]a\n[00:00.50]b", LrcParser.serialize(lines, -1500L))
    }

    @Test
    fun `active index follows the playhead`() {
        val lines = listOf(LyricLine(0L, "a"), LyricLine(1000L, "b"), LyricLine(2000L, "c"))
        assertEquals(-1, LrcParser.activeIndex(lines, -10L))
        assertEquals(0, LrcParser.activeIndex(lines, 500L))
        assertEquals(1, LrcParser.activeIndex(lines, 1000L))
        assertEquals(2, LrcParser.activeIndex(lines, 9999L))
    }

    @Test
    fun `build classifies plain lyrics preserving lines`() {
        val lyrics = LrcParser.build("Line 1\n\nLine 2", pl.dakil.music.domain.model.LyricsSource.METADATA_PLAIN)
        assertFalse(lyrics.synced)
        assertEquals(3, lyrics.lines.size)
        assertEquals("Line 1", lyrics.lines[0].text)
    }
}
