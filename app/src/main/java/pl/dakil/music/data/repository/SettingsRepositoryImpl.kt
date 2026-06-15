package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            forceDarkTheme = prefs[KEY_FORCE_DARK] ?: false,
            gaplessPlayback = prefs[KEY_GAPLESS] ?: true,
            rememberSortState = prefs[KEY_REMEMBER_SORT] ?: false,
        )
    }

    override suspend fun setDynamicColor(enabled: Boolean) =
        edit(KEY_DYNAMIC_COLOR, enabled)

    override suspend fun setForceDarkTheme(enabled: Boolean) =
        edit(KEY_FORCE_DARK, enabled)

    override suspend fun setGaplessPlayback(enabled: Boolean) =
        edit(KEY_GAPLESS, enabled)

    override suspend fun setRememberSortState(enabled: Boolean) =
        edit(KEY_REMEMBER_SORT, enabled)

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_FORCE_DARK = booleanPreferencesKey("force_dark")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_REMEMBER_SORT = booleanPreferencesKey("remember_sort_state")
    }
}
