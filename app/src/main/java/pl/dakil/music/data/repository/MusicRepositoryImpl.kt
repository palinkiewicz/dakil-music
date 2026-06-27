package pl.dakil.music.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.dakil.music.data.mediastore.MediaStoreDataSource
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AlbumRule
import pl.dakil.music.domain.model.NO_ALBUM_ID
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SongFileInfo
import pl.dakil.music.domain.repository.AlbumRuleRepository
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.domain.repository.MusicRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.util.AlbumAuthors
import pl.dakil.music.domain.util.AlbumKey

/**
 * Caches the MediaStore scan in memory and exposes derived collections. Albums and
 * performers are computed from the cached song list, so a single [refresh] keeps
 * every screen consistent. Album author and per-song cover-art mode are resolved
 * from the cover-art settings and any per-album rule overrides.
 */
class MusicRepositoryImpl(
    private val dataSource: MediaStoreDataSource,
    private val settingsRepository: SettingsRepository,
    private val albumRuleRepository: AlbumRuleRepository,
) : MusicRepository {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    private val refreshMutex = Mutex()

    /** Raw scan; used by history/statistics reconciliation (no cover-art annotation). */
    override val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    override val annotatedSongs: Flow<List<Song>> = combine(
        _songs,
        settingsRepository.settings,
        albumRuleRepository.rules,
    ) { songs, settings, rules -> annotate(songs, settings, rules) }

    override val albums: Flow<List<Album>> = combine(
        _songs,
        settingsRepository.settings,
        albumRuleRepository.rules,
    ) { songs, settings, rules ->
        val (withoutAlbum, withAlbum) = songs.partition { it.album.isBlank() }
        val ruleByKey = rules.associateBy { it.albumKey }

        val realAlbums = withAlbum.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                val mode = ruleByKey[AlbumKey.of(albumSongs)]?.authorMode ?: settings.albumAuthorMode
                val authors = AlbumAuthors.authors(albumSongs, mode)
                Album(
                    id = albumId,
                    title = first.album,
                    artist = authors.joinToString(", "),
                    artworkUri = first.albumArtUri,
                    songCount = albumSongs.size,
                    durationMs = albumSongs.sumOf { it.durationMs },
                    year = albumSongs.maxOf { it.year },
                    authors = authors,
                )
            }
            .sortedBy { it.title.lowercase() }

        // A single synthetic "No album" entry, surfaced first, for untagged songs.
        val noAlbum = if (withoutAlbum.isEmpty()) {
            null
        } else {
            Album(
                id = NO_ALBUM_ID,
                title = "",
                artist = "",
                artworkUri = null,
                songCount = withoutAlbum.size,
                durationMs = withoutAlbum.sumOf { it.durationMs },
            )
        }

        listOfNotNull(noAlbum) + realAlbums
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

    override fun songsForAlbum(albumId: Long): Flow<List<Song>> = annotatedSongs.map { songs ->
        if (albumId == NO_ALBUM_ID) {
            songs.filter { it.album.isBlank() }.sortedBy { it.title.lowercase() }
        } else {
            songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }
        }
    }

    override fun songsForPerformer(performerName: String): Flow<List<Song>> = annotatedSongs.map { songs ->
        songs.filter { song -> song.artists.any { it.equals(performerName, ignoreCase = true) } }
            .sortedBy { it.title.lowercase() }
    }

    override fun songsByIds(ids: Collection<Long>): Flow<List<Song>> = annotatedSongs.map { songs ->
        val wanted = ids.toHashSet()
        songs.filter { it.id in wanted }
    }

    override suspend fun refresh() {
        // Serialize concurrent refreshes (e.g. tapping "Refresh" rapidly).
        refreshMutex.withLock {
            _songs.value = dataSource.queryAudio()
        }
    }

    override suspend fun fileInfo(songs: List<Song>): List<SongFileInfo> = dataSource.fileInfo(songs)

    /** Sets [Song.individualCoverArt] per the effective cover-art mode of each song's album. */
    private fun annotate(songs: List<Song>, settings: AppSettings, rules: List<AlbumRule>): List<Song> {
        val ruleByKey = rules.associateBy { it.albumKey }
        // Group once so every song of an album resolves to the same (deterministic) key.
        val modeByAlbumId = songs.filter { it.album.isNotBlank() }
            .groupBy { it.albumId }
            .mapValues { (_, albumSongs) ->
                ruleByKey[AlbumKey.of(albumSongs)]?.coverArtMode ?: settings.albumCoverArtMode
            }
        return songs.map { song ->
            val mode = if (song.album.isBlank()) settings.albumCoverArtMode else modeByAlbumId[song.albumId]
            song.copy(individualCoverArt = mode == AlbumCoverArtMode.INDIVIDUAL)
        }
    }
}
