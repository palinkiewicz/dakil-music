package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavDefaults
import pl.dakil.music.domain.model.NavEntry
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.repository.NavigationConfigRepository

/**
 * Each component's ordered entries are persisted as one string, encoded as
 * `ITEM:0|1` segments joined by commas, e.g. `NOW_PLAYING:1,LIBRARY:1,MORE:1`.
 * Order is preserved; on read the stored list is reconciled against the current
 * catalog so newly-added items appear automatically (see [NavDefaults.reconcile]).
 */
class NavigationConfigRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : NavigationConfigRepository {

    override val config: Flow<NavConfig> = dataStore.data
        .map { prefs ->
            NavConfig(NavComponent.entries.associateWith { component -> readEntries(prefs, component) })
        }
        .distinctUntilChanged()

    override suspend fun setComponent(component: NavComponent, entries: List<NavEntry>) {
        dataStore.edit { prefs ->
            prefs[keyFor(component)] = encode(NavDefaults.reconcile(component, entries))
        }
    }

    private fun readEntries(prefs: Preferences, component: NavComponent): List<NavEntry> {
        val stored = prefs[keyFor(component)]?.let(::decode) ?: emptyList()
        return NavDefaults.reconcile(component, stored)
    }

    private fun encode(entries: List<NavEntry>): String =
        entries.joinToString(",") { "${it.item.name}:${if (it.enabled) 1 else 0}" }

    private fun decode(raw: String): List<NavEntry> =
        raw.split(",").mapNotNull { segment ->
            val (name, flag) = segment.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val item = runCatching { NavItem.valueOf(name) }.getOrNull() ?: return@mapNotNull null
            NavEntry(item, flag == "1")
        }

    private fun keyFor(component: NavComponent) = stringPreferencesKey("nav_${component.name}")
}
