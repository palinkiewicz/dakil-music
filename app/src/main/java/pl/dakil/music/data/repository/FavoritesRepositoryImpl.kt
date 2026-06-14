package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.repository.FavoritesRepository

/**
 * Favorites persisted as a set of stringified song ids in DataStore. Ids are kept
 * as strings because Preferences only supports a String set; the boundary maps
 * them back to [Long] for the domain.
 */
class FavoritesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : FavoritesRepository {

    override val favoriteIds: Flow<Set<Long>> = dataStore.data
        .map { prefs -> prefs[KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet() }
        .distinctUntilChanged()

    override fun isFavorite(songId: Long): Flow<Boolean> =
        favoriteIds.map { songId in it }.distinctUntilChanged()

    override suspend fun toggle(songId: Long) {
        dataStore.edit { prefs ->
            val current = prefs[KEY].orEmpty().toMutableSet()
            val key = songId.toString()
            if (!current.add(key)) current.remove(key)
            prefs[KEY] = current
        }
    }

    override suspend fun setFavorite(songIds: Collection<Long>, favorite: Boolean) {
        if (songIds.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY].orEmpty().toMutableSet()
            val keys = songIds.map { it.toString() }
            if (favorite) current.addAll(keys) else current.removeAll(keys.toSet())
            prefs[KEY] = current
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("favorite_song_ids")
    }
}
