package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.repository.FavoritesRepository

/**
 * Favorites persisted as an *ordered* comma-separated list of song ids so the user can
 * reorder them. A legacy unordered string-set key is migrated on first read/write and
 * kept in sync for membership lookups.
 */
class FavoritesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : FavoritesRepository {

    override val favoriteOrder: Flow<List<Long>> = dataStore.data
        .map { prefs -> readOrder(prefs) }
        .distinctUntilChanged()

    override val favoriteIds: Flow<Set<Long>> = favoriteOrder
        .map { it.toSet() }
        .distinctUntilChanged()

    override fun isFavorite(songId: Long): Flow<Boolean> =
        favoriteIds.map { songId in it }.distinctUntilChanged()

    override suspend fun toggle(songId: Long) {
        dataStore.edit { prefs ->
            val current = readOrder(prefs).toMutableList()
            if (!current.remove(songId)) current.add(songId)
            writeOrder(prefs, current)
        }
    }

    override suspend fun setFavorite(songIds: Collection<Long>, favorite: Boolean) {
        if (songIds.isEmpty()) return
        dataStore.edit { prefs ->
            val current = readOrder(prefs).toMutableList()
            if (favorite) {
                songIds.forEach { if (it !in current) current.add(it) }
            } else {
                current.removeAll(songIds.toSet())
            }
            writeOrder(prefs, current)
        }
    }

    override suspend fun reorder(from: Int, to: Int) {
        if (from == to) return
        dataStore.edit { prefs ->
            val current = readOrder(prefs).toMutableList()
            if (from in current.indices && to in current.indices) {
                current.add(to, current.removeAt(from))
                writeOrder(prefs, current)
            }
        }
    }

    /** Reads the ordered list, falling back to (migrating from) the legacy set. */
    private fun readOrder(prefs: Preferences): List<Long> {
        prefs[ORDER_KEY]?.split(",")?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return prefs[LEGACY_KEY]?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    private fun writeOrder(prefs: androidx.datastore.preferences.core.MutablePreferences, list: List<Long>) {
        prefs[ORDER_KEY] = list.joinToString(",")
        // Keep the legacy set in sync so any reader of the old key stays correct.
        prefs[LEGACY_KEY] = list.mapTo(HashSet()) { it.toString() }
    }

    private companion object {
        val LEGACY_KEY = stringSetPreferencesKey("favorite_song_ids")
        val ORDER_KEY = stringPreferencesKey("favorite_song_ids_order")
    }
}
