package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.StatisticsWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pure computation of statistics [StatisticsWindow]s (epoch-millis ranges) and of
 * the selectable year/month/week lists, in a given timezone. Kept Android-free so
 * it can be unit tested.
 */
object StatWindows {

    val ALL_TIME = StatisticsWindow(from = 0L, to = Long.MAX_VALUE)

    fun yearWindow(year: Int, zone: ZoneId): StatisticsWindow {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return StatisticsWindow(start, end - 1)
    }

    fun monthWindow(month: YearMonth, zone: ZoneId): StatisticsWindow {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return StatisticsWindow(start, end - 1)
    }

    /** Window for the 7-day week beginning on [weekStart]. */
    fun weekWindow(weekStart: LocalDate, zone: ZoneId): StatisticsWindow {
        val start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        return StatisticsWindow(start, end - 1)
    }

    /** The first day of the week containing [date], given [firstDayOfWeek]. */
    fun weekStartOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /** Years from the most recent down to the one containing [earliestMillis]. */
    fun availableYears(earliestMillis: Long, today: LocalDate, zone: ZoneId): List<Int> {
        val firstYear = Instant.ofEpochMilli(earliestMillis).atZone(zone).year
        return (today.year downTo firstYear).toList()
    }

    fun availableMonths(earliestMillis: Long, today: LocalDate, zone: ZoneId): List<YearMonth> {
        val first = Instant.ofEpochMilli(earliestMillis).atZone(zone).let { YearMonth.of(it.year, it.month) }
        val current = YearMonth.of(today.year, today.month)
        val result = ArrayList<YearMonth>()
        var m = current
        while (!m.isBefore(first)) {
            result.add(m)
            m = m.minusMonths(1)
        }
        return result
    }

    /** Week-start dates from the most recent down to the week of [earliestMillis]. */
    fun availableWeeks(
        earliestMillis: Long,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        zone: ZoneId,
    ): List<LocalDate> {
        val firstDate = Instant.ofEpochMilli(earliestMillis).atZone(zone).toLocalDate()
        val firstWeek = weekStartOf(firstDate, firstDayOfWeek)
        val result = ArrayList<LocalDate>()
        var w = weekStartOf(today, firstDayOfWeek)
        while (!w.isBefore(firstWeek)) {
            result.add(w)
            w = w.minusWeeks(1)
        }
        return result
    }
}
