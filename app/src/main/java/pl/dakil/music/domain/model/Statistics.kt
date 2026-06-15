package pl.dakil.music.domain.model

import android.net.Uri

/** Whether charts/routines are ranked and measured by listened seconds or play count. */
enum class StatMetric { SECONDS, PLAYS }

/** The kind of time window the statistics cover. */
enum class StatRangeType { ALL_TIME, YEARLY, MONTHLY, WEEKLY }

/** Default time range presets offered in Settings. */
enum class StatDefaultRange {
    ALL_TIME,
    THIS_YEAR,
    PREVIOUS_YEAR,
    THIS_MONTH,
    PREVIOUS_MONTH,
    THIS_WEEK,
    PREVIOUS_WEEK,
}

/** Half-open-ish window [from, to] in epoch millis (both inclusive at the edges). */
data class StatisticsWindow(val from: Long, val to: Long)

data class TrackStat(
    val songId: Long,
    val title: String,
    val artists: List<String>,
    val albumId: Long,
    val albumArtUri: Uri?,
    val seconds: Long,
    val plays: Long,
) {
    fun value(metric: StatMetric): Long = if (metric == StatMetric.SECONDS) seconds else plays
}

data class ArtistStat(
    val name: String,
    val seconds: Long,
    val plays: Long,
) {
    fun value(metric: StatMetric): Long = if (metric == StatMetric.SECONDS) seconds else plays
}

data class AlbumStat(
    val albumId: Long,
    val album: String,
    val albumArtUri: Uri?,
    val seconds: Long,
    val plays: Long,
) {
    fun value(metric: StatMetric): Long = if (metric == StatMetric.SECONDS) seconds else plays
}

/**
 * Aggregated statistics for a time window. [topTracks]/[topArtists]/[topAlbums] are
 * fully ranked (the UI shows the top 5 and reveals the rest on demand).
 * [hourHistogram] has 24 buckets (0..23, local time); [weekdayHistogram] has 7
 * buckets ordered starting at the configured first day of week. Both carry the
 * value of the selected metric.
 */
data class Statistics(
    val totalSeconds: Long,
    val totalPlays: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val topTracks: List<TrackStat>,
    val topArtists: List<ArtistStat>,
    val topAlbums: List<AlbumStat>,
    val hourHistogram: LongArray,
    val weekdayHistogram: LongArray,
) {
    val isEmpty: Boolean get() = totalPlays == 0L && totalSeconds == 0L

    companion object {
        val EMPTY = Statistics(
            totalSeconds = 0L,
            totalPlays = 0L,
            distinctSongs = 0,
            distinctArtists = 0,
            topTracks = emptyList(),
            topArtists = emptyList(),
            topAlbums = emptyList(),
            hourHistogram = LongArray(24),
            weekdayHistogram = LongArray(7),
        )
    }
}
