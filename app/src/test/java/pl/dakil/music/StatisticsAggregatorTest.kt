package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.util.StatisticsAggregator
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatisticsAggregatorTest {

    private val zone = ZoneId.of("UTC")

    private fun millis(y: Int, mo: Int, d: Int, h: Int): Long =
        LocalDateTime.of(y, mo, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun record(
        songId: Long,
        ts: Long,
        seconds: Int,
        plays: Int,
        artists: List<String>,
        albumId: Long = 1,
    ) = ListeningRecord(
        songId = songId,
        startTimestamp = ts,
        secondsPlayed = seconds,
        timesPlayed = plays,
        title = "song$songId",
        artists = artists,
        album = "album$albumId",
        albumId = albumId,
        albumArtUri = null,
        durationMs = 0L,
        contentKey = "",
    )

    // 2025-09-01 is a Monday, 2025-09-02 a Tuesday.
    private val records = listOf(
        record(1, millis(2025, 9, 1, 10), seconds = 100, plays = 1, artists = listOf("X")),
        record(1, millis(2025, 9, 1, 11), seconds = 50, plays = 2, artists = listOf("X")),
        record(2, millis(2025, 9, 2, 10), seconds = 200, plays = 1, artists = listOf("X", "Y"), albumId = 2),
    )

    @Test
    fun totals_andDistinctCounts() {
        val stats = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.MONDAY, zone)
        assertEquals(350L, stats.totalSeconds)
        assertEquals(4L, stats.totalPlays)
        assertEquals(2, stats.distinctSongs)
        assertEquals(2, stats.distinctArtists) // X, Y
    }

    @Test
    fun topTracks_rankedByMetric() {
        val byDuration = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.MONDAY, zone)
        assertEquals(2L, byDuration.topTracks.first().songId) // 200s > 150s

        val byPlays = StatisticsAggregator.aggregate(records, StatMetric.PLAYS, DayOfWeek.MONDAY, zone)
        assertEquals(1L, byPlays.topTracks.first().songId) // 3 plays > 1 play
    }

    @Test
    fun topArtists_foldMultiValue() {
        val stats = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.MONDAY, zone)
        val x = stats.topArtists.first { it.name == "X" }
        assertEquals(350L, x.seconds) // 100 + 50 + 200
    }

    @Test
    fun hourHistogram_bucketsByLocalHour() {
        val stats = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.MONDAY, zone)
        assertEquals(300L, stats.hourHistogram[10]) // 100 + 200
        assertEquals(50L, stats.hourHistogram[11])
    }

    @Test
    fun weekdayHistogram_respectsFirstDayOfWeek() {
        val stats = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.MONDAY, zone)
        assertEquals(150L, stats.weekdayHistogram[0]) // Monday bucket
        assertEquals(200L, stats.weekdayHistogram[1]) // Tuesday bucket

        val sundayFirst = StatisticsAggregator.aggregate(records, StatMetric.SECONDS, DayOfWeek.SUNDAY, zone)
        assertEquals(150L, sundayFirst.weekdayHistogram[1]) // Monday is now index 1
        assertEquals(200L, sundayFirst.weekdayHistogram[2]) // Tuesday index 2
    }
}
