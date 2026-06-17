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
import pl.dakil.music.domain.repository.UserPlaylistRepository

/** Observe all albums for the Albums tab. */
class GetAlbumsUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(): Flow<List<Album>> = musicRepository.albums
}

/** Observe all performers (already split out of the artist metadata). */
class GetPerformersUseCase(private val musicRepository: MusicRepository) {
    operator fun invoke(): Flow<List<Performer>> = musicRepository.performers
}

/**
 * Builds the playlists shown in the Playlists tab: the two system playlists followed
 * by every user playlist. Song counts are derived live so the UI stays accurate.
 */
class GetPlaylistsUseCase(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> = combine(
        musicRepository.songs,
        favoritesRepository.favoriteIds,
        userPlaylistRepository.playlists,
    ) { songs, favorites, userPlaylists ->
        val libraryIds = songs.mapTo(HashSet()) { it.id }
        val durationById = songs.associate { it.id to it.durationMs }
        buildList {
            add(
                Playlist(
                    systemType = SystemPlaylist.ALL_SONGS,
                    songCount = songs.size,
                    durationMs = songs.sumOf { it.durationMs },
                ),
            )
            val favoriteSongs = songs.filter { it.id in favorites }
            add(
                Playlist(
                    systemType = SystemPlaylist.FAVORITES,
                    songCount = favoriteSongs.size,
                    durationMs = favoriteSongs.sumOf { it.durationMs },
                ),
            )
            userPlaylists.forEach { playlist ->
                val presentIds = playlist.songIds.filter { it in libraryIds }
                add(
                    Playlist(
                        userPlaylist = playlist,
                        songCount = presentIds.size,
                        durationMs = presentIds.sumOf { durationById[it] ?: 0L },
                    ),
                )
            }
        }
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
        SystemPlaylist.ALL_SONGS -> musicRepository.annotatedSongs
        SystemPlaylist.FAVORITES -> combine(
            musicRepository.annotatedSongs,
            favoritesRepository.favoriteOrder,
        ) { songs, order ->
            val byId = songs.associateBy { it.id }
            order.mapNotNull { byId[it] }
        }
    }
}

/** Songs of a user playlist, resolved against the library and kept in playlist order. */
class GetUserPlaylistSongsUseCase(
    private val musicRepository: MusicRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
) {
    operator fun invoke(playlistId: String): Flow<List<Song>> = combine(
        musicRepository.annotatedSongs,
        userPlaylistRepository.playlists,
    ) { songs, playlists ->
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return@combine emptyList()
        val byId = songs.associateBy { it.id }
        playlist.songIds.mapNotNull { byId[it] }
    }
}
