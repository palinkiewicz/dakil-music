package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.AlbumStat
import pl.dakil.music.domain.model.ArtistStat
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.TrackStat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Pure aggregation of listening records into [Statistics]. Kept Android-free and
 * deterministic (timezone and first-day-of-week are injected) so it can be unit
 * tested. Hour/weekday bucketing uses [java.time] in the supplied [zone] rather
 * than SQL `localtime`, which can't honor a configurable first day of week.
 */
object StatisticsAggregator {

    private class Acc(var seconds: Long = 0L, var plays: Long = 0L)

    fun aggregate(
        records: List<ListeningRecord>,
        metric: StatMetric,
        firstDayOfWeek: DayOfWeek,
        zone: ZoneId,
    ): Statistics {
        if (records.isEmpty()) return Statistics.EMPTY

        var totalSeconds = 0L
        var totalPlays = 0L

        val trackAcc = LinkedHashMap<Long, Acc>()
        val trackMeta = HashMap<Long, ListeningRecord>()
        val albumAcc = LinkedHashMap<Long, Acc>()
        val albumMeta = HashMap<Long, ListeningRecord>()
        // Artists are multi-valued per record: key by lowercase, keep first display casing.
        val artistAcc = LinkedHashMap<String, Acc>()
        val artistDisplay = HashMap<String, String>()

        val hours = LongArray(24)
        val weekdays = LongArray(7)

        for (r in records) {
            val seconds = r.secondsPlayed.toLong()
            val plays = r.timesPlayed.toLong()
            totalSeconds += seconds
            totalPlays += plays

            trackAcc.getOrPut(r.songId) { Acc() }.let { it.seconds += seconds; it.plays += plays }
            trackMeta.putIfAbsent(r.songId, r)

            albumAcc.getOrPut(r.albumId) { Acc() }.let { it.seconds += seconds; it.plays += plays }
            albumMeta.putIfAbsent(r.albumId, r)

            for (artist in r.artists) {
                val key = artist.lowercase()
                artistAcc.getOrPut(key) { Acc() }.let { it.seconds += seconds; it.plays += plays }
                artistDisplay.putIfAbsent(key, artist)
            }

            val dateTime = Instant.ofEpochMilli(r.startTimestamp).atZone(zone)
            val metricValue = if (metric == StatMetric.SECONDS) seconds else plays
            hours[dateTime.hour] += metricValue
            val offset = (dateTime.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            weekdays[offset] += metricValue
        }

        val topTracks = trackAcc.map { (id, acc) ->
            val meta = trackMeta.getValue(id)
            TrackStat(id, meta.title, meta.artists, meta.albumId, meta.albumArtUri, acc.seconds, acc.plays)
        }.sortedByMetric(metric) { it.seconds to it.plays }

        val topAlbums = albumAcc.map { (id, acc) ->
            val meta = albumMeta.getValue(id)
            AlbumStat(id, meta.album, meta.albumArtUri, acc.seconds, acc.plays)
        }.sortedByMetric(metric) { it.seconds to it.plays }

        val topArtists = artistAcc.map { (key, acc) ->
            ArtistStat(artistDisplay.getValue(key), acc.seconds, acc.plays)
        }.sortedByMetric(metric) { it.seconds to it.plays }

        return Statistics(
            totalSeconds = totalSeconds,
            totalPlays = totalPlays,
            distinctSongs = trackAcc.size,
            distinctArtists = artistAcc.size,
            topTracks = topTracks,
            topArtists = topArtists,
            topAlbums = topAlbums,
            hourHistogram = hours,
            weekdayHistogram = weekdays,
        )
    }

    private inline fun <T> List<T>.sortedByMetric(
        metric: StatMetric,
        crossinline selector: (T) -> Pair<Long, Long>,
    ): List<T> = sortedByDescending {
        val (seconds, plays) = selector(it)
        if (metric == StatMetric.SECONDS) seconds else plays
    }
}
