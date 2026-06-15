package pl.dakil.music.domain.repository

import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.StatisticsWindow
import java.time.DayOfWeek

/**
 * Local-only listening history. Records are written by the playback tracker and
 * read by the History and Statistics screens. The merge operations
 * ([reconcile], [propagateRetag], [mergeInto]) keep records linked to live
 * MediaStore songs as tags change and ids churn.
 */
interface ListeningHistoryRepository {

    suspend fun record(record: ListeningRecord)

    suspend fun count(): Int

    /** A page of history, newest first. */
    suspend fun page(limit: Int, offset: Int): List<ListeningRecord>

    /** Epoch millis of the oldest record, or null when history is empty. */
    suspend fun earliestTimestamp(): Long?

    suspend fun statistics(
        window: StatisticsWindow,
        metric: StatMetric,
        firstDayOfWeek: DayOfWeek,
    ): Statistics

    suspend fun clearAll()

    suspend fun exportRecords(): List<ListeningRecord>

    suspend fun importRecords(records: List<ListeningRecord>)

    /** Re-links history records whose song id no longer exists to a re-added song
     *  with a matching content key. */
    suspend fun reconcile(liveSongs: List<Song>)

    /** Refreshes the snapshot of every record belonging to the given (retagged) songs. */
    suspend fun propagateRetag(songs: List<Song>)

    /** Folds a deleted song's records into [target] (manual "merge with existing"). */
    suspend fun mergeInto(deletedSongId: Long, target: Song)
}
