package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavEntry
import pl.dakil.music.domain.repository.NavigationConfigRepository
import pl.dakil.music.domain.repository.SettingsRepository

class ObserveNavigationConfigUseCase(private val repository: NavigationConfigRepository) {
    operator fun invoke(): Flow<NavConfig> = repository.config
}

class UpdateNavComponentUseCase(private val repository: NavigationConfigRepository) {
    suspend operator fun invoke(component: NavComponent, entries: List<NavEntry>) =
        repository.setComponent(component, entries)
}

/** Moves a layout left on the old defaults to the current ones, once; run at startup. */
class UpgradeLegacyNavigationUseCase(
    private val repository: NavigationConfigRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() =
        repository.upgradeLegacyDefaults(miniPlayerShown = settingsRepository.settings.first().showMiniPlayer)
}
