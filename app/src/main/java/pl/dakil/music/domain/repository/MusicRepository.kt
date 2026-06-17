package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Song

/**
 * Read access to the on-device music library backed by MediaStore. Implementations
 * cache the scan result and expose derived collections (albums, performers) as
 * cold/hot flows so the UI updates automatically after a [refresh].
 */
interface MusicRepository {

    /** All songs, sorted by title. Emits a new list whenever the library is refreshed. */
    val songs: Flow<List<Song>>

    /**
     * Same as [songs] but with each song's [Song.individualCoverArt] resolved from the
     * cover-art settings/rule for its album. Use this for any UI that renders per-song
     * cover art; [songs] stays raw for history/statistics reconciliation.
     */
    val annotatedSongs: Flow<List<Song>>

    val albums: Flow<List<Album>>

    /** Distinct performers derived from the split artist metadata. */
    val performers: Flow<List<Performer>>

    fun songsForAlbum(albumId: Long): Flow<List<Song>>

    fun songsForPerformer(performerName: String): Flow<List<Song>>

    fun songsByIds(ids: Collection<Long>): Flow<List<Song>>

    /** Re-queries MediaStore. Suspends until the scan completes. */
    suspend fun refresh()
}
