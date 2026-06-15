package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.data.csv.ListeningHistoryCsv
import pl.dakil.music.domain.model.ListeningRecord

class ListeningHistoryCsvTest {

    private fun record(title: String, artists: List<String>, album: String) = ListeningRecord(
        songId = 42,
        startTimestamp = 1_700_000_000_000L,
        secondsPlayed = 123,
        timesPlayed = 2,
        title = title,
        artists = artists,
        album = album,
        albumId = 7,
        albumArtUri = null,
        durationMs = 200_000L,
        contentKey = "",
    )

    @Test
    fun roundTrip_preservesFields() {
        val original = record("My Song", listOf("A", "B"), "Album")
        val row = ListeningHistoryCsv.toRow(original)
        val parsed = ListeningHistoryCsv.parseRow(row)!!
        assertEquals(original.songId, parsed.songId)
        assertEquals(original.startTimestamp, parsed.startTimestamp)
        assertEquals(original.secondsPlayed, parsed.secondsPlayed)
        assertEquals(original.timesPlayed, parsed.timesPlayed)
        assertEquals(original.title, parsed.title)
        assertEquals(original.artists, parsed.artists)
        assertEquals(original.album, parsed.album)
        assertEquals(original.albumId, parsed.albumId)
    }

    @Test
    fun roundTrip_handlesCommasAndQuotes() {
        val original = record("Title, with comma \"and quote\"", listOf("Artist, Jr."), "Album")
        val parsed = ListeningHistoryCsv.parseRow(ListeningHistoryCsv.toRow(original))!!
        assertEquals(original.title, parsed.title)
        assertEquals(original.artists, parsed.artists)
    }

    @Test
    fun header_validation() {
        assertTrue(ListeningHistoryCsv.isValidHeader(ListeningHistoryCsv.header))
        assertFalse(ListeningHistoryCsv.isValidHeader("not,a,valid,header"))
    }

    @Test
    fun malformedRow_returnsNull() {
        assertNull(ListeningHistoryCsv.parseRow("only,three,columns"))
        assertNull(ListeningHistoryCsv.parseRow("abc,1,2,3,t,a,al,4,,5")) // bad timestamp
    }
}
