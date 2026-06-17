package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.repository.FavoritesRepository

class ObserveFavoritesUseCase(private val repository: FavoritesRepository) {
    operator fun invoke(): Flow<Set<Long>> = repository.favoriteIds
}

class IsFavoriteUseCase(private val repository: FavoritesRepository) {
    operator fun invoke(songId: Long): Flow<Boolean> = repository.isFavorite(songId)
}

class ToggleFavoriteUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(songId: Long) = repository.toggle(songId)
}

/** Bulk favorite/unfavorite, used by the library multi-select action bar. */
class SetFavoritesUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(songIds: Collection<Long>, favorite: Boolean) =
        repository.setFavorite(songIds, favorite)
}

class ReorderFavoritesUseCase(private val repository: FavoritesRepository) {
    suspend operator fun invoke(from: Int, to: Int) = repository.reorder(from, to)
}
