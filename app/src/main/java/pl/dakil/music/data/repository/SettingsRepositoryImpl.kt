package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AppColorTheme
import pl.dakil.music.domain.model.DarkThemeOption
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            // Migrated from the old dynamic_color/force_dark booleans for existing installs;
            // new keys take precedence once the user picks a theme explicitly.
            colorTheme = prefs[KEY_COLOR_THEME]
                ?.let { name -> AppColorTheme.entries.firstOrNull { it.name == name } }
                ?: if (prefs[KEY_DYNAMIC_COLOR] == true) AppColorTheme.DYNAMIC else AppColorTheme.DAKILS_MUSIC,
            darkThemeOption = prefs[KEY_DARK_THEME_OPTION]
                ?.let { name -> DarkThemeOption.entries.firstOrNull { it.name == name } }
                ?: if (prefs[KEY_FORCE_DARK] == true) DarkThemeOption.DARK else DarkThemeOption.FOLLOW_SYSTEM,
            pureBlack = prefs[KEY_PURE_BLACK] ?: false,
            gaplessPlayback = prefs[KEY_GAPLESS] ?: true,
            autoPauseOnZeroVolume = prefs[KEY_AUTO_PAUSE_ZERO_VOLUME] ?: true,
            autoResumeOnVolumeRestored = prefs[KEY_AUTO_RESUME_VOLUME] ?: true,
            rememberSortState = prefs[KEY_REMEMBER_SORT] ?: false,
            albumColumns = prefs[KEY_ALBUM_COLUMNS] ?: 2,
            statisticsEnabled = prefs[KEY_STATS_ENABLED] ?: true,
            minPlaySeconds = prefs[KEY_MIN_PLAY_SECONDS] ?: 10,
            historyUpdateSeconds = prefs[KEY_HISTORY_UPDATE_SECONDS] ?: 5,
            firstDayOfWeek = prefs[KEY_FIRST_DAY_OF_WEEK] ?: 1,
            statsDefaultRange = prefs[KEY_STATS_RANGE]
                ?.let { name -> StatDefaultRange.entries.firstOrNull { it.name == name } }
                ?: StatDefaultRange.ALL_TIME,
            statsDefaultMetric = prefs[KEY_STATS_METRIC]
                ?.let { name -> StatMetric.entries.firstOrNull { it.name == name } }
                ?: StatMetric.SECONDS,
            queueRemoveMode = prefs[KEY_QUEUE_REMOVE_MODE]
                ?.let { name -> QueueRemoveMode.entries.firstOrNull { it.name == name } }
                ?: QueueRemoveMode.SWIPE,
            albumCoverArtMode = prefs[KEY_ALBUM_COVER_ART_MODE]
                ?.let { name -> AlbumCoverArtMode.entries.firstOrNull { it.name == name } }
                ?: AlbumCoverArtMode.SHARED,
            albumAuthorMode = prefs[KEY_ALBUM_AUTHOR_MODE]
                ?.let { name -> AlbumAuthorMode.entries.firstOrNull { it.name == name } }
                ?: AlbumAuthorMode.FIRST_SONG_ARTISTS,
            albumCornerRoundnessDp = prefs[KEY_ALBUM_CORNER_DP] ?: 16,
            nowPlayingCornerRoundnessDp = prefs[KEY_NOW_PLAYING_CORNER_DP] ?: 32,
            displayLyrics = prefs[KEY_DISPLAY_LYRICS] ?: true,
            fetchMissingLyricsFromLrclib = prefs[KEY_FETCH_MISSING_LYRICS] ?: true,
        )
    }

    override suspend fun setColorTheme(theme: AppColorTheme) {
        dataStore.edit { it[KEY_COLOR_THEME] = theme.name }
    }

    override suspend fun setDarkThemeOption(option: DarkThemeOption) {
        dataStore.edit { it[KEY_DARK_THEME_OPTION] = option.name }
    }

    override suspend fun setPureBlack(enabled: Boolean) =
        edit(KEY_PURE_BLACK, enabled)

    override suspend fun setGaplessPlayback(enabled: Boolean) =
        edit(KEY_GAPLESS, enabled)

    override suspend fun setAutoPauseOnZeroVolume(enabled: Boolean) =
        edit(KEY_AUTO_PAUSE_ZERO_VOLUME, enabled)

    override suspend fun setAutoResumeOnVolumeRestored(enabled: Boolean) =
        edit(KEY_AUTO_RESUME_VOLUME, enabled)

    override suspend fun setRememberSortState(enabled: Boolean) =
        edit(KEY_REMEMBER_SORT, enabled)

    override suspend fun setAlbumColumns(count: Int) {
        dataStore.edit { it[KEY_ALBUM_COLUMNS] = count }
    }

    override suspend fun setStatisticsEnabled(enabled: Boolean) =
        edit(KEY_STATS_ENABLED, enabled)

    override suspend fun setMinPlaySeconds(seconds: Int) {
        dataStore.edit { it[KEY_MIN_PLAY_SECONDS] = seconds }
    }

    override suspend fun setHistoryUpdateSeconds(seconds: Int) {
        dataStore.edit { it[KEY_HISTORY_UPDATE_SECONDS] = seconds }
    }

    override suspend fun setFirstDayOfWeek(isoDayOfWeek: Int) {
        dataStore.edit { it[KEY_FIRST_DAY_OF_WEEK] = isoDayOfWeek }
    }

    override suspend fun setStatsDefaultRange(range: StatDefaultRange) {
        dataStore.edit { it[KEY_STATS_RANGE] = range.name }
    }

    override suspend fun setStatsDefaultMetric(metric: StatMetric) {
        dataStore.edit { it[KEY_STATS_METRIC] = metric.name }
    }

    override suspend fun setQueueRemoveMode(mode: QueueRemoveMode) {
        dataStore.edit { it[KEY_QUEUE_REMOVE_MODE] = mode.name }
    }

    override suspend fun setAlbumCoverArtMode(mode: AlbumCoverArtMode) {
        dataStore.edit { it[KEY_ALBUM_COVER_ART_MODE] = mode.name }
    }

    override suspend fun setAlbumAuthorMode(mode: AlbumAuthorMode) {
        dataStore.edit { it[KEY_ALBUM_AUTHOR_MODE] = mode.name }
    }

    override suspend fun setAlbumCornerRoundnessDp(dp: Int) {
        dataStore.edit { it[KEY_ALBUM_CORNER_DP] = dp }
    }

    override suspend fun setNowPlayingCornerRoundnessDp(dp: Int) {
        dataStore.edit { it[KEY_NOW_PLAYING_CORNER_DP] = dp }
    }

    override suspend fun setDisplayLyrics(enabled: Boolean) =
        edit(KEY_DISPLAY_LYRICS, enabled)

    override suspend fun setFetchMissingLyricsFromLrclib(enabled: Boolean) =
        edit(KEY_FETCH_MISSING_LYRICS, enabled)

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        // Legacy keys, kept only as a fallback for the read-time migration above.
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_FORCE_DARK = booleanPreferencesKey("force_dark")
        val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        val KEY_DARK_THEME_OPTION = stringPreferencesKey("dark_theme_option")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_AUTO_PAUSE_ZERO_VOLUME = booleanPreferencesKey("auto_pause_zero_volume")
        val KEY_AUTO_RESUME_VOLUME = booleanPreferencesKey("auto_resume_volume")
        val KEY_REMEMBER_SORT = booleanPreferencesKey("remember_sort_state")
        val KEY_ALBUM_COLUMNS = intPreferencesKey("album_columns")
        val KEY_STATS_ENABLED = booleanPreferencesKey("statistics_enabled")
        val KEY_MIN_PLAY_SECONDS = intPreferencesKey("min_play_seconds")
        val KEY_HISTORY_UPDATE_SECONDS = intPreferencesKey("history_update_seconds")
        val KEY_FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        val KEY_STATS_RANGE = stringPreferencesKey("stats_default_range")
        val KEY_STATS_METRIC = stringPreferencesKey("stats_default_metric")
        val KEY_QUEUE_REMOVE_MODE = stringPreferencesKey("queue_remove_mode")
        val KEY_ALBUM_COVER_ART_MODE = stringPreferencesKey("album_cover_art_mode")
        val KEY_ALBUM_AUTHOR_MODE = stringPreferencesKey("album_author_mode")
        val KEY_ALBUM_CORNER_DP = intPreferencesKey("album_corner_roundness_dp")
        val KEY_NOW_PLAYING_CORNER_DP = intPreferencesKey("now_playing_corner_roundness_dp")
        val KEY_DISPLAY_LYRICS = booleanPreferencesKey("display_lyrics")
        val KEY_FETCH_MISSING_LYRICS = booleanPreferencesKey("fetch_missing_lyrics_lrclib")
    }
}
