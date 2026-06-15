package pl.dakil.music.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.StatRangeType
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.TrackStat
import pl.dakil.music.domain.util.StatWindows
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class StatisticsUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val rangeType: StatRangeType = StatRangeType.ALL_TIME,
    val metric: StatMetric = StatMetric.SECONDS,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val years: List<Int> = emptyList(),
    val months: List<YearMonth> = emptyList(),
    val weeks: List<LocalDate> = emptyList(),
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedWeek: LocalDate = LocalDate.now(),
    val statistics: Statistics = Statistics.EMPTY,
)

class StatisticsViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _state.asStateFlow()

    /** Live library, used to flag deleted top-tracks and back the merge picker. */
    val liveSongs: StateFlow<List<Song>> = container.musicRepository.songs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mergeTarget = MutableStateFlow<TrackStat?>(null)
    val mergeTarget: StateFlow<TrackStat?> = _mergeTarget.asStateFlow()

    fun startMerge(track: TrackStat) {
        _mergeTarget.value = track
    }

    fun dismissMerge() {
        _mergeTarget.value = null
    }

    fun mergeInto(deletedSongId: Long, target: Song) {
        viewModelScope.launch {
            container.mergeHistory(deletedSongId, target)
            _mergeTarget.value = null
            refresh()
        }
    }

    init {
        viewModelScope.launch {
            val settings = container.observeSettings().first()
            val firstDow = DayOfWeek.of(settings.firstDayOfWeek.coerceIn(1, 7))
            val earliest = container.getEarliestHistoryDate()
            if (earliest == null) {
                _state.update { it.copy(loading = false, empty = true, firstDayOfWeek = firstDow) }
                return@launch
            }
            val today = LocalDate.now(zone)
            val years = StatWindows.availableYears(earliest, today, zone)
            val months = StatWindows.availableMonths(earliest, today, zone)
            val weeks = StatWindows.availableWeeks(earliest, today, firstDow, zone)

            val seeded = seedDefault(settings.statsDefaultRange, today, firstDow)
            _state.value = StatisticsUiState(
                loading = true,
                empty = false,
                rangeType = seeded.rangeType,
                metric = settings.statsDefaultMetric,
                firstDayOfWeek = firstDow,
                years = years,
                months = months,
                weeks = weeks,
                selectedYear = seeded.year,
                selectedMonth = seeded.month,
                selectedWeek = seeded.week,
            )
            refresh()
        }
    }

    fun setRangeType(type: StatRangeType) {
        _state.update { it.copy(rangeType = type) }
        refresh()
    }

    fun setMetric(metric: StatMetric) {
        _state.update { it.copy(metric = metric) }
        refresh()
    }

    fun selectYear(year: Int) {
        _state.update { it.copy(selectedYear = year) }
        refresh()
    }

    fun selectMonth(month: YearMonth) {
        _state.update { it.copy(selectedMonth = month) }
        refresh()
    }

    fun selectWeek(weekStart: LocalDate) {
        _state.update { it.copy(selectedWeek = weekStart) }
        refresh()
    }

    private fun refresh() {
        val s = _state.value
        if (s.empty) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val window = when (s.rangeType) {
                StatRangeType.ALL_TIME -> StatWindows.ALL_TIME
                StatRangeType.YEARLY -> StatWindows.yearWindow(s.selectedYear, zone)
                StatRangeType.MONTHLY -> StatWindows.monthWindow(s.selectedMonth, zone)
                StatRangeType.WEEKLY -> StatWindows.weekWindow(s.selectedWeek, zone)
            }
            val stats = container.getStatistics(window, s.metric, s.firstDayOfWeek)
            _state.update { it.copy(loading = false, statistics = stats) }
        }
    }

    private data class Seed(
        val rangeType: StatRangeType,
        val year: Int,
        val month: YearMonth,
        val week: LocalDate,
    )

    private fun seedDefault(range: StatDefaultRange, today: LocalDate, firstDow: DayOfWeek): Seed {
        val thisMonth = YearMonth.from(today)
        val thisWeek = StatWindows.weekStartOf(today, firstDow)
        return when (range) {
            StatDefaultRange.ALL_TIME ->
                Seed(StatRangeType.ALL_TIME, today.year, thisMonth, thisWeek)
            StatDefaultRange.THIS_YEAR ->
                Seed(StatRangeType.YEARLY, today.year, thisMonth, thisWeek)
            StatDefaultRange.PREVIOUS_YEAR ->
                Seed(StatRangeType.YEARLY, today.year - 1, thisMonth, thisWeek)
            StatDefaultRange.THIS_MONTH ->
                Seed(StatRangeType.MONTHLY, today.year, thisMonth, thisWeek)
            StatDefaultRange.PREVIOUS_MONTH ->
                Seed(StatRangeType.MONTHLY, today.year, thisMonth.minusMonths(1), thisWeek)
            StatDefaultRange.THIS_WEEK ->
                Seed(StatRangeType.WEEKLY, today.year, thisMonth, thisWeek)
            StatDefaultRange.PREVIOUS_WEEK ->
                Seed(StatRangeType.WEEKLY, today.year, thisMonth, thisWeek.minusWeeks(1))
        }
    }
}
