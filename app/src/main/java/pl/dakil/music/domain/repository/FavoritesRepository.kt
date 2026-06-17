package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow

/** Persists the set of favorite song ids (DataStore-backed) and exposes it globally. */
interface FavoritesRepository {

    val favoriteIds: Flow<Set<Long>>

    /** Favorites in user-defined order (drives the Favorites playlist and reordering). */
    val favoriteOrder: Flow<List<Long>>

    fun isFavorite(songId: Long): Flow<Boolean>

    suspend fun toggle(songId: Long)

    /** Bulk add (used by the library multi-select action). */
    suspend fun setFavorite(songIds: Collection<Long>, favorite: Boolean)

    /** Moves the favorite at index [from] to index [to]. */
    suspend fun reorder(from: Int, to: Int)
}
