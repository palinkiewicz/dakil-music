package pl.dakil.music.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
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

    fun setRememberSortState(enabled: Boolean) = viewModelScope.launch {
        container.updateSettings.setRememberSortState(enabled)
    }

    fun setAlbumColumns(count: Int) = viewModelScope.launch {
        container.updateSettings.setAlbumColumns(count)
    }
}
