package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavEntry
import pl.dakil.music.domain.repository.NavigationConfigRepository

class ObserveNavigationConfigUseCase(private val repository: NavigationConfigRepository) {
    operator fun invoke(): Flow<NavConfig> = repository.config
}

class UpdateNavComponentUseCase(private val repository: NavigationConfigRepository) {
    suspend operator fun invoke(component: NavComponent, entries: List<NavEntry>) =
        repository.setComponent(component, entries)
}
