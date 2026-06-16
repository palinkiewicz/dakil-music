package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric

/** User preferences (DataStore-backed). All values have sensible defaults. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setForceDarkTheme(enabled: Boolean)

    suspend fun setGaplessPlayback(enabled: Boolean)

    suspend fun setRememberSortState(enabled: Boolean)

    suspend fun setAlbumColumns(count: Int)

    suspend fun setStatisticsEnabled(enabled: Boolean)

    suspend fun setMinPlaySeconds(seconds: Int)

    suspend fun setHistoryUpdateSeconds(seconds: Int)

    suspend fun setFirstDayOfWeek(isoDayOfWeek: Int)

    suspend fun setStatsDefaultRange(range: StatDefaultRange)

    suspend fun setStatsDefaultMetric(metric: StatMetric)

    suspend fun setQueueRemoveMode(mode: QueueRemoveMode)
}

data class AppSettings(
    val dynamicColor: Boolean = true,
    val forceDarkTheme: Boolean = false,
    val gaplessPlayback: Boolean = true,
    val rememberSortState: Boolean = false,
    val albumColumns: Int = 2,
    /** When false, no listening history is recorded. */
    val statisticsEnabled: Boolean = true,
    /** Sessions shorter than this are discarded (0..30, step 5). */
    val minPlaySeconds: Int = 10,
    /** How often the active session is checkpointed to disk; 0 disables it (0..15s). */
    val historyUpdateSeconds: Int = 5,
    /** ISO day-of-week (Mon=1 .. Sun=7) used for weekly analytics. */
    val firstDayOfWeek: Int = 1,
    val statsDefaultRange: StatDefaultRange = StatDefaultRange.ALL_TIME,
    val statsDefaultMetric: StatMetric = StatMetric.SECONDS,
    /** How a song is removed from the play queue. */
    val queueRemoveMode: QueueRemoveMode = QueueRemoveMode.SWIPE,
)
