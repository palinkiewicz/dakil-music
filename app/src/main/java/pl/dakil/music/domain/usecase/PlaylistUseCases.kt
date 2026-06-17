package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.repository.UserPlaylistRepository

class ObserveUserPlaylistsUseCase(private val repository: UserPlaylistRepository) {
    operator fun invoke(): Flow<List<UserPlaylist>> = repository.playlists
}

class CreatePlaylistUseCase(private val repository: UserPlaylistRepository) {
    /** Returns the new playlist id. */
    suspend operator fun invoke(name: String): String = repository.create(name)
}

class RenamePlaylistUseCase(private val repository: UserPlaylistRepository) {
    suspend operator fun invoke(id: String, newName: String) = repository.rename(id, newName)
}

class DeletePlaylistUseCase(private val repository: UserPlaylistRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

class AddSongsToPlaylistUseCase(private val repository: UserPlaylistRepository) {
    suspend operator fun invoke(id: String, songIds: List<Long>) = repository.addSongs(id, songIds)
}

class RemoveSongsFromPlaylistUseCase(private val repository: UserPlaylistRepository) {
    suspend operator fun invoke(id: String, songIds: List<Long>) = repository.removeSongs(id, songIds)
}

class ReorderPlaylistUseCase(private val repository: UserPlaylistRepository) {
    suspend operator fun invoke(id: String, from: Int, to: Int) = repository.reorder(id, from, to)
}
