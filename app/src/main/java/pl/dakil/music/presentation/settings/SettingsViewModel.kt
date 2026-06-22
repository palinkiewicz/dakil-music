package pl.dakil.music.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.StatDefaultRange
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.repository.AppSettings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setDynamicColor(enabled)
    }

    fun setForceDarkTheme(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setForceDarkTheme(enabled)
    }

    fun setGaplessPlayback(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setGaplessPlayback(enabled)
    }

    fun setAutoPauseOnZeroVolume(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setAutoPauseOnZeroVolume(enabled)
    }

    fun setAutoResumeOnVolumeRestored(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setAutoResumeOnVolumeRestored(enabled)
    }

    fun setRememberSortState(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setRememberSortState(enabled)
    }

    fun setAlbumColumns(count: Int) = viewModelScope.launch {
        container.updateSettings.setAlbumColumns(count)
    }

    fun setStatisticsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setStatisticsEnabled(enabled)
        // Disabling wipes all locally-stored history.
        if (!enabled) container.clearHistory()
    }

    fun setMinPlaySeconds(seconds: Int) = viewModelScope.launch {
        container.updateSettings.setMinPlaySeconds(seconds)
    }

    fun setHistoryUpdateSeconds(seconds: Int) = viewModelScope.launch {
        container.updateSettings.setHistoryUpdateSeconds(seconds)
    }

    fun setFirstDayOfWeek(isoDayOfWeek: Int) = viewModelScope.launch {
        container.updateSettings.setFirstDayOfWeek(isoDayOfWeek)
    }

    fun setStatsDefaultRange(range: StatDefaultRange) = viewModelScope.launch {
        container.updateSettings.setStatsDefaultRange(range)
    }

    fun setStatsDefaultMetric(metric: StatMetric) = viewModelScope.launch {
        container.updateSettings.setStatsDefaultMetric(metric)
    }

    fun setQueueRemoveMode(mode: QueueRemoveMode) = viewModelScope.launch {
        container.updateSettings.setQueueRemoveMode(mode)
    }

    fun setAlbumCoverArtMode(mode: AlbumCoverArtMode) = viewModelScope.launch {
        container.updateSettings.setAlbumCoverArtMode(mode)
    }

    fun setAlbumAuthorMode(mode: AlbumAuthorMode) = viewModelScope.launch {
        container.updateSettings.setAlbumAuthorMode(mode)
    }

    fun setAlbumCornerRoundnessDp(dp: Int) = viewModelScope.launch {
        container.updateSettings.setAlbumCornerRoundnessDp(dp)
    }

    fun setNowPlayingCornerRoundnessDp(dp: Int) = viewModelScope.launch {
        container.updateSettings.setNowPlayingCornerRoundnessDp(dp)
    }
}
