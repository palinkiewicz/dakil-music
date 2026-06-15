package pl.dakil.music.data.repository

import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.music.data.db.ArtistsCodec
import pl.dakil.music.data.db.ListeningRecordDao
import pl.dakil.music.data.db.ListeningRecordEntity
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.StatisticsWindow
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import pl.dakil.music.domain.util.ContentKey
import pl.dakil.music.domain.util.StatisticsAggregator
import java.time.DayOfWeek
import java.time.ZoneId

class ListeningHistoryRepositoryImpl(
    private val dao: ListeningRecordDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ListeningHistoryRepository {

    override suspend fun record(record: ListeningRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun upsert(record: ListeningRecord): Long =
        if (record.id == 0L) {
            dao.insert(record.toEntity())
        } else {
            dao.update(record.toEntity())
            record.id
        }

    override fun changes(): Flow<Unit> = dao.observeCount().map { }

    override suspend fun count(): Int = dao.count()

    override suspend fun page(limit: Int, offset: Int): List<ListeningRecord> =
        dao.page(limit, offset).map { it.toDomain() }

    override suspend fun earliestTimestamp(): Long? = dao.earliestTimestamp()

    override suspend fun statistics(
        window: StatisticsWindow,
        metric: StatMetric,
        firstDayOfWeek: DayOfWeek,
    ): Statistics {
        val records = dao.rowsInWindow(window.from, window.to).map { it.toDomain() }
        return StatisticsAggregator.aggregate(records, metric, firstDayOfWeek, zone)
    }

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun exportRecords(): List<ListeningRecord> =
        dao.exportAll().map { it.toDomain() }

    override suspend fun importRecords(records: List<ListeningRecord>) {
        records.map { it.toEntity(resetId = true) }
            .chunked(IMPORT_CHUNK)
            .forEach { dao.insertAll(it) }
    }

    override suspend fun reconcile(liveSongs: List<Song>) {
        if (liveSongs.isEmpty()) return
        val liveIds = liveSongs.mapTo(HashSet()) { it.id }
        val byContentKey = HashMap<String, Song>()
        for (song in liveSongs) byContentKey.putIfAbsent(ContentKey.of(song), song)

        for (ref in dao.distinctSongRefs()) {
            if (ref.songId in liveIds) continue
            val match = byContentKey[ref.contentKey] ?: continue
            dao.reassign(ref.songId, match)
        }
    }

    override suspend fun propagateRetag(songs: List<Song>) {
        for (song in songs) dao.reassign(song.id, song)
    }

    override suspend fun mergeInto(deletedSongId: Long, target: Song) {
        dao.reassign(deletedSongId, target)
    }

    // --- Mapping -------------------------------------------------------------------

    private suspend fun ListeningRecordDao.reassign(matchSongId: Long, target: Song) {
        reassignSong(
            matchSongId = matchSongId,
            newSongId = target.id,
            title = target.title,
            artists = ArtistsCodec.encode(target.artists),
            album = target.album,
            albumId = target.albumId,
            albumArtUri = target.albumArtUri?.toString(),
            durationMs = target.durationMs,
            contentKey = ContentKey.of(target),
        )
    }

    private fun ListeningRecord.toEntity(resetId: Boolean = false): ListeningRecordEntity =
        ListeningRecordEntity(
            id = if (resetId) 0 else id,
            songId = songId,
            startTimestamp = startTimestamp,
            secondsPlayed = secondsPlayed,
            timesPlayed = timesPlayed,
            title = title,
            artists = ArtistsCodec.encode(artists),
            album = album,
            albumId = albumId,
            albumArtUri = albumArtUri?.toString(),
            durationMs = durationMs,
            contentKey = contentKey.ifEmpty { ContentKey.of(title, artists, album) },
        )

    private fun ListeningRecordEntity.toDomain(): ListeningRecord =
        ListeningRecord(
            id = id,
            songId = songId,
            startTimestamp = startTimestamp,
            secondsPlayed = secondsPlayed,
            timesPlayed = timesPlayed,
            title = title,
            artists = ArtistsCodec.decode(artists),
            album = album,
            albumId = albumId,
            albumArtUri = albumArtUri?.toUri(),
            durationMs = durationMs,
            contentKey = contentKey,
        )

    private companion object {
        const val IMPORT_CHUNK = 500
    }
}
