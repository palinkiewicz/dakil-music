package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow

/** User preferences (DataStore-backed). All values have sensible defaults. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setForceDarkTheme(enabled: Boolean)

    suspend fun setGaplessPlayback(enabled: Boolean)

    suspend fun setRememberSortState(enabled: Boolean)
}

data class AppSettings(
    val dynamicColor: Boolean = true,
    val forceDarkTheme: Boolean = false,
    val gaplessPlayback: Boolean = true,
    val rememberSortState: Boolean = false,
)
