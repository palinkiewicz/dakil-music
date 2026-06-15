package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.SettingsRepository

class ObserveSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

class UpdateSettingsUseCase(private val repository: SettingsRepository) {
    suspend fun setDynamicColor(enabled: Boolean) = repository.setDynamicColor(enabled)
    suspend fun setForceDarkTheme(enabled: Boolean) = repository.setForceDarkTheme(enabled)
    suspend fun setGaplessPlayback(enabled: Boolean) = repository.setGaplessPlayback(enabled)
    suspend fun setRememberSortState(enabled: Boolean) = repository.setRememberSortState(enabled)
    suspend fun setAlbumColumns(count: Int) = repository.setAlbumColumns(count)
    suspend fun setStatisticsEnabled(enabled: Boolean) = repository.setStatisticsEnabled(enabled)
    suspend fun setMinPlaySeconds(seconds: Int) = repository.setMinPlaySeconds(seconds)
    suspend fun setFirstDayOfWeek(isoDayOfWeek: Int) = repository.setFirstDayOfWeek(isoDayOfWeek)
    suspend fun setStatsDefaultRange(range: StatDefaultRange) = repository.setStatsDefaultRange(range)
    suspend fun setStatsDefaultMetric(metric: StatMetric) = repository.setStatsDefaultMetric(metric)
}
