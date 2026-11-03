package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavEntry

/** Stores the user's per-component navigation customization. */
interface NavigationConfigRepository {
    val config: Flow<NavConfig>

    /** Replaces the ordered entry list for a single [component]. */
    suspend fun setComponent(component: NavComponent, entries: List<NavEntry>)

    /**
     * Once per install, moves a saved layout still on the old defaults to the current ones
     * (see [pl.dakil.music.domain.model.NavDefaults.upgradeLegacyDefaults]).
     */
    suspend fun upgradeLegacyDefaults(miniPlayerShown: Boolean)
}
