package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SongFileInfo
import pl.dakil.music.domain.repository.MusicRepository

/** Resolves filesystem details (path, size, bitrate, format) for the given songs. */
class GetSongFileInfoUseCase(private val musicRepository: MusicRepository) {
    suspend operator fun invoke(songs: List<Song>): List<SongFileInfo> = musicRepository.fileInfo(songs)
}
