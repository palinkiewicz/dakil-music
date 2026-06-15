package pl.dakil.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One listening session: a contiguous period during which a single song was the
 * current track. Looping a track (repeat-one) keeps the same record and bumps
 * [timesPlayed]; switching tracks opens a new record.
 *
 * Snapshot fields ([title], [artists], [album], …) are copied in at record time
 * so a song that is later deleted from MediaStore can still be displayed, and so
 * [contentKey] can re-link history when MediaStore reassigns ids.
 */
@Entity(
    tableName = "listening_record",
    indices = [
        Index("startTimestamp"),
        Index("songId"),
        Index("contentKey"),
    ],
)
data class ListeningRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    /** Session start, epoch millis (UTC). */
    val startTimestamp: Long,
    /** Actual listened time (paused time excluded), whole seconds. */
    val secondsPlayed: Int,
    /** Number of plays within the session; >= 1 (loops bump this). */
    val timesPlayed: Int,
    val title: String,
    val artists: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: String?,
    val durationMs: Long,
    /** Normalized title|artists|album identity for self-healing merges. */
    val contentKey: String,
)

/** Distinct (songId, contentKey) pair used by reconciliation. */
data class SongRef(
    val songId: Long,
    val contentKey: String,
)
