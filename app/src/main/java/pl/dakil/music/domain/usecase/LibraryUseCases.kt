package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.repository.FavoritesRepository
import pl.dakil.music.domain.repository.MusicRepository

/** Observe all albums for the Albums tab. */
class GetAlbumsUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(): Flow<List<Album>> = musicRepository.albums
}

/** Observe all performers (already split out of the artist metadata). */
class GetPerformersUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(): Flow<List<Performer>> = musicRepository.performers
}

/**
 * Builds the two system playlists. Their song counts are derived live from the
 * library + favorites so the UI stays accurate without a dedicated table.
 */
class GetPlaylistsUseCase(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> =
        combine(musicRepository.songs, favoritesRepository.favoriteIds) { songs, favorites ->
            listOf(
                Playlist(SystemPlaylist.ALL_SONGS, songs.size),
                Playlist(SystemPlaylist.FAVORITES, songs.count { it.id in favorites }),
            )
        }
}

class GetSongsForAlbumUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(albumId: Long): Flow<List<Song>> = musicRepository.songsForAlbum(albumId)
}

class GetSongsForPerformerUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(performerName: String): Flow<List<Song>> =
        musicRepository.songsForPerformer(performerName)
}

class GetSongsForPlaylistUseCase(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(playlist: SystemPlaylist): Flow<List<Song>> = when (playlist) {
        SystemPlaylist.ALL_SONGS -> musicRepository.songs
        SystemPlaylist.FAVORITES -> combine(
            musicRepository.songs,
            favoritesRepository.favoriteIds,
        ) { songs, favorites -> songs.filter { it.id in favorites } }
    }
}
