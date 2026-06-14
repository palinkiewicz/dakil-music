package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.repository.MusicRepository

/** Triggers a fresh MediaStore scan; suspends until done so callers can show progress. */
class RefreshLibraryUseCase(private val musicRepository: MusicRepository) {
    suspend operator fun invoke() = musicRepository.refresh()
}
