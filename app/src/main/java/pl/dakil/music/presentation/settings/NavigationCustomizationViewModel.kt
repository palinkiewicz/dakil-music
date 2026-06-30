package pl.dakil.music.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavDefaults
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.model.NavRules

class NavigationCustomizationViewModel(private val container: AppContainer) : ViewModel() {

    val config: StateFlow<NavConfig> = container.observeNavigationConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavConfig.DEFAULT)

    fun setEnabled(component: NavComponent, item: NavItem, enabled: Boolean) {
        val current = config.value
        // Never toggle a locked-on item off, and never exceed the bottom-bar cap.
        if (!enabled && item in NavRules.lockedItems(current, component)) return
        if (enabled && component == NavComponent.BOTTOM_BAR &&
            item !in current.enabled(component) &&
            current.enabled(component).size >= NavDefaults.MAX_BOTTOM_BAR
        ) return

        val updated = current.entries(component)
            .map { if (it.item == item) it.copy(enabled = enabled) else it }
        persistDiff(current, normalize(current.withComponent(component, updated)))
    }

    fun move(component: NavComponent, from: Int, to: Int) {
        val current = config.value
        val entries = current.entries(component).toMutableList()
        if (from !in entries.indices || to !in entries.indices) return
        entries.add(to, entries.removeAt(from))
        persistDiff(current, current.withComponent(component, entries))
    }

    /** Force More onto the bottom bar when it is the only remaining path to Settings. */
    private fun normalize(config: NavConfig): NavConfig {
        val settingsOnlyInMore = NavItem.SETTINGS in config.enabled(NavComponent.MORE_SCREEN) &&
            NavItem.SETTINGS !in config.enabled(NavComponent.BOTTOM_BAR)
        if (!settingsOnlyInMore) return config

        val bottomBar = config.entries(NavComponent.BOTTOM_BAR)
            .map { if (it.item == NavItem.MORE) it.copy(enabled = true) else it }
        return config.withComponent(NavComponent.BOTTOM_BAR, bottomBar)
    }

    private fun persistDiff(old: NavConfig, new: NavConfig) = viewModelScope.launch {
        for (component in NavComponent.entries) {
            if (old.entries(component) != new.entries(component)) {
                container.updateNavComponent(component, new.entries(component))
            }
        }
    }
}
