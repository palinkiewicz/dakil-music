package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AppColorTheme
import pl.dakil.music.domain.model.DarkThemeOption
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric

/** User preferences (DataStore-backed). All values have sensible defaults. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setColorTheme(theme: AppColorTheme)

    suspend fun setDarkThemeOption(option: DarkThemeOption)

    suspend fun setPureBlack(enabled: Boolean)

    suspend fun setGaplessPlayback(enabled: Boolean)

    suspend fun setAutoPauseOnZeroVolume(enabled: Boolean)

    suspend fun setAutoResumeOnVolumeRestored(enabled: Boolean)

    suspend fun setRememberSortState(enabled: Boolean)

    suspend fun setAlbumColumns(count: Int)

    suspend fun setStatisticsEnabled(enabled: Boolean)

    suspend fun setMinPlaySeconds(seconds: Int)

    suspend fun setHistoryUpdateSeconds(seconds: Int)

    suspend fun setFirstDayOfWeek(isoDayOfWeek: Int)

    suspend fun setStatsDefaultRange(range: StatDefaultRange)

    suspend fun setStatsDefaultMetric(metric: StatMetric)

    suspend fun setQueueRemoveMode(mode: QueueRemoveMode)

    suspend fun setAlbumCoverArtMode(mode: AlbumCoverArtMode)

    suspend fun setAlbumAuthorMode(mode: AlbumAuthorMode)

    suspend fun setAlbumCornerRoundnessDp(dp: Int)

    suspend fun setNowPlayingCornerRoundnessDp(dp: Int)

    suspend fun setDisplayLyrics(enabled: Boolean)

    suspend fun setFetchMissingLyricsFromLrclib(enabled: Boolean)
}

data class AppSettings(
    val colorTheme: AppColorTheme = AppColorTheme.DAKILS_MUSIC,
    val darkThemeOption: DarkThemeOption = DarkThemeOption.FOLLOW_SYSTEM,
    /** Fully black background in dark mode (OLED battery saving); ignored when [darkThemeOption] is LIGHT. */
    val pureBlack: Boolean = false,
    val gaplessPlayback: Boolean = true,
    /** Pause playback when the device media volume reaches 0%. */
    val autoPauseOnZeroVolume: Boolean = true,
    /** Resume playback when the media volume is raised above 0% again (only if auto-paused). */
    val autoResumeOnVolumeRestored: Boolean = true,
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
    /** Whether album tracks share one cover art or show their own embedded art. */
    val albumCoverArtMode: AlbumCoverArtMode = AlbumCoverArtMode.SHARED,
    /** How an album's displayed author is derived from its songs. */
    val albumAuthorMode: AlbumAuthorMode = AlbumAuthorMode.FIRST_SONG_ARTISTS,
    /** Corner roundness (dp) of album cover art, 0..64. */
    val albumCornerRoundnessDp: Int = 16,
    /** Corner roundness (dp) of the Now Playing cover art, 0..64. */
    val nowPlayingCornerRoundnessDp: Int = 32,
    /** Show a lyrics card on Now Playing and fetch lyrics when a song starts. */
    val displayLyrics: Boolean = true,
    /** Fall back to the lrclib.net API when a song has no embedded lyrics. */
    val fetchMissingLyricsFromLrclib: Boolean = true,
)
