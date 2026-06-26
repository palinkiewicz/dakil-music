package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.QueueRemoveMode
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
    suspend fun setAutoPauseOnZeroVolume(enabled: Boolean) = repository.setAutoPauseOnZeroVolume(enabled)
    suspend fun setAutoResumeOnVolumeRestored(enabled: Boolean) = repository.setAutoResumeOnVolumeRestored(enabled)
    suspend fun setRememberSortState(enabled: Boolean) = repository.setRememberSortState(enabled)
    suspend fun setAlbumColumns(count: Int) = repository.setAlbumColumns(count)
    suspend fun setStatisticsEnabled(enabled: Boolean) = repository.setStatisticsEnabled(enabled)
    suspend fun setMinPlaySeconds(seconds: Int) = repository.setMinPlaySeconds(seconds)
    suspend fun setHistoryUpdateSeconds(seconds: Int) = repository.setHistoryUpdateSeconds(seconds)
    suspend fun setFirstDayOfWeek(isoDayOfWeek: Int) = repository.setFirstDayOfWeek(isoDayOfWeek)
    suspend fun setStatsDefaultRange(range: StatDefaultRange) = repository.setStatsDefaultRange(range)
    suspend fun setStatsDefaultMetric(metric: StatMetric) = repository.setStatsDefaultMetric(metric)
    suspend fun setQueueRemoveMode(mode: QueueRemoveMode) = repository.setQueueRemoveMode(mode)
    suspend fun setAlbumCoverArtMode(mode: AlbumCoverArtMode) = repository.setAlbumCoverArtMode(mode)
    suspend fun setAlbumAuthorMode(mode: AlbumAuthorMode) = repository.setAlbumAuthorMode(mode)
    suspend fun setAlbumCornerRoundnessDp(dp: Int) = repository.setAlbumCornerRoundnessDp(dp)
    suspend fun setNowPlayingCornerRoundnessDp(dp: Int) = repository.setNowPlayingCornerRoundnessDp(dp)
    suspend fun setDisplayLyrics(enabled: Boolean) = repository.setDisplayLyrics(enabled)
    suspend fun setFetchMissingLyricsFromLrclib(enabled: Boolean) = repository.setFetchMissingLyricsFromLrclib(enabled)
}
