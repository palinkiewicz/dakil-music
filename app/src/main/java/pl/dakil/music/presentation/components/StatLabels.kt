package pl.dakil.music.presentation.components

import androidx.annotation.StringRes
import pl.dakil.music.R
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.StatRangeType

/** Maps an ISO day-of-week (Mon=1 .. Sun=7) to its localized name resource. */
@StringRes
fun weekdayNameRes(isoDayOfWeek: Int): Int = when (isoDayOfWeek) {
    1 -> R.string.weekday_monday
    2 -> R.string.weekday_tuesday
    3 -> R.string.weekday_wednesday
    4 -> R.string.weekday_thursday
    5 -> R.string.weekday_friday
    6 -> R.string.weekday_saturday
    else -> R.string.weekday_sunday
}

/** Short weekday label resource for chart axes. */
@StringRes
fun weekdayShortNameRes(isoDayOfWeek: Int): Int = when (isoDayOfWeek) {
    1 -> R.string.weekday_short_monday
    2 -> R.string.weekday_short_tuesday
    3 -> R.string.weekday_short_wednesday
    4 -> R.string.weekday_short_thursday
    5 -> R.string.weekday_short_friday
    6 -> R.string.weekday_short_saturday
    else -> R.string.weekday_short_sunday
}

@StringRes
fun statMetricNameRes(metric: StatMetric): Int = when (metric) {
    StatMetric.SECONDS -> R.string.stats_metric_duration
    StatMetric.PLAYS -> R.string.stats_metric_plays
}

@StringRes
fun statRangeTypeNameRes(range: StatRangeType): Int = when (range) {
    StatRangeType.ALL_TIME -> R.string.stats_range_all_time
    StatRangeType.YEARLY -> R.string.stats_range_yearly
    StatRangeType.MONTHLY -> R.string.stats_range_monthly
    StatRangeType.WEEKLY -> R.string.stats_range_weekly
}

@StringRes
fun queueRemoveModeNameRes(mode: QueueRemoveMode): Int = when (mode) {
    QueueRemoveMode.MENU -> R.string.queue_remove_mode_menu
    QueueRemoveMode.BUTTON -> R.string.queue_remove_mode_button
    QueueRemoveMode.SWIPE -> R.string.queue_remove_mode_swipe
}

@StringRes
fun albumAuthorModeNameRes(mode: AlbumAuthorMode): Int = when (mode) {
    AlbumAuthorMode.FIRST_SONG_ARTISTS -> R.string.album_author_mode_first_song_artists
    AlbumAuthorMode.FIRST_ARTIST_OF_FIRST_SONG -> R.string.album_author_mode_first_artist
    AlbumAuthorMode.MOST_COMMON -> R.string.album_author_mode_most_common
    AlbumAuthorMode.ALL_ARTISTS -> R.string.album_author_mode_all_artists
}

@StringRes
fun statDefaultRangeNameRes(range: StatDefaultRange): Int = when (range) {
    StatDefaultRange.ALL_TIME -> R.string.stats_range_all_time
    StatDefaultRange.THIS_YEAR -> R.string.stats_default_this_year
    StatDefaultRange.PREVIOUS_YEAR -> R.string.stats_default_previous_year
    StatDefaultRange.THIS_MONTH -> R.string.stats_default_this_month
    StatDefaultRange.PREVIOUS_MONTH -> R.string.stats_default_previous_month
    StatDefaultRange.THIS_WEEK -> R.string.stats_default_this_week
    StatDefaultRange.PREVIOUS_WEEK -> R.string.stats_default_previous_week
}
