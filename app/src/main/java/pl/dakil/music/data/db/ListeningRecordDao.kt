package pl.dakil.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ListeningRecordDao {

    @Insert
    suspend fun insert(record: ListeningRecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<ListeningRecordEntity>)

    @Query("SELECT COUNT(*) FROM listening_record")
    suspend fun count(): Int

    /** A page of history, newest first. */
    @Query("SELECT * FROM listening_record ORDER BY startTimestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ListeningRecordEntity>

    /** Rows within a time window, used for all statistics aggregation in Kotlin. */
    @Query("SELECT * FROM listening_record WHERE startTimestamp BETWEEN :from AND :to")
    suspend fun rowsInWindow(from: Long, to: Long): List<ListeningRecordEntity>

    @Query("SELECT MIN(startTimestamp) FROM listening_record")
    suspend fun earliestTimestamp(): Long?

    @Query("SELECT * FROM listening_record ORDER BY startTimestamp ASC")
    suspend fun exportAll(): List<ListeningRecordEntity>

    @Query("SELECT DISTINCT songId, contentKey FROM listening_record")
    suspend fun distinctSongRefs(): List<SongRef>

    @Query("DELETE FROM listening_record")
    suspend fun deleteAll()

    /**
     * Rewrites every record currently filed under [matchSongId] to a new identity.
     * Serves all three merge paths: retag (match == new), auto-reconcile and
     * manual merge (match != new).
     */
    @Query(
        """
        UPDATE listening_record
        SET songId = :newSongId, title = :title, artists = :artists, album = :album,
            albumId = :albumId, albumArtUri = :albumArtUri, durationMs = :durationMs,
            contentKey = :contentKey
        WHERE songId = :matchSongId
        """,
    )
    suspend fun reassignSong(
        matchSongId: Long,
        newSongId: Long,
        title: String,
        artists: String,
        album: String,
        albumId: Long,
        albumArtUri: String?,
        durationMs: Long,
        contentKey: String,
    )
}
