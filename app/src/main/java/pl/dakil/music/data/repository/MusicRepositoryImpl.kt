package pl.dakil.music.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.dakil.music.data.mediastore.MediaStoreDataSource
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.MusicRepository

/**
 * Caches the MediaStore scan in memory and exposes derived collections. Albums and
 * performers are computed from the cached song list, so a single [refresh] keeps
 * every screen consistent. Derivations are plain [map]s — cheap and lazy.
 */
class MusicRepositoryImpl(
    private val dataSource: MediaStoreDataSource,
) : MusicRepository {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    private val refreshMutex = Mutex()

    override val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    override val albums: Flow<List<Album>> = _songs.map { songs ->
        songs.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = first.rawArtist,
                    artworkUri = first.albumArtUri,
                    songCount = albumSongs.size,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    override val performers: Flow<List<Performer>> = _songs.map { songs ->
        val counts = HashMap<String, Int>()
        for (song in songs) {
            for (artist in song.artists) {
                counts[artist] = (counts[artist] ?: 0) + 1
            }
        }
        counts.map { (name, count) -> Performer(name, count) }
            .sortedBy { it.name.lowercase() }
    }

    override fun songsForAlbum(albumId: Long): Flow<List<Song>> = _songs.map { songs ->
        songs.filter { it.albumId == albumId }
            .sortedBy { it.trackNumber }
    }

    override fun songsForPerformer(performerName: String): Flow<List<Song>> = _songs.map { songs ->
        songs.filter { song -> song.artists.any { it.equals(performerName, ignoreCase = true) } }
            .sortedBy { it.title.lowercase() }
    }

    override fun songsByIds(ids: Collection<Long>): Flow<List<Song>> = _songs.map { songs ->
        val wanted = ids.toHashSet()
        songs.filter { it.id in wanted }
    }

    override suspend fun refresh() {
        // Serialize concurrent refreshes (e.g. tapping "Refresh" rapidly).
        refreshMutex.withLock {
            _songs.value = dataSource.queryAudio()
        }
    }
}
