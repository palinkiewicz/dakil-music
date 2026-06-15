package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.StatMetric
import pl.dakil.music.domain.model.Statistics
import pl.dakil.music.domain.model.StatisticsWindow
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import java.time.DayOfWeek

class RecordListeningSessionUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(record: ListeningRecord) = repository.record(record)
}

class ObserveHistoryChangesUseCase(private val repository: ListeningHistoryRepository) {
    operator fun invoke(): Flow<Unit> = repository.changes()
}

class GetHistoryPageUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(limit: Int, offset: Int): List<ListeningRecord> =
        repository.page(limit, offset)
}

class GetHistoryCountUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(): Int = repository.count()
}

class GetEarliestHistoryDateUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(): Long? = repository.earliestTimestamp()
}

class GetStatisticsUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(
        window: StatisticsWindow,
        metric: StatMetric,
        firstDayOfWeek: DayOfWeek,
    ): Statistics = repository.statistics(window, metric, firstDayOfWeek)
}

class ClearHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke() = repository.clearAll()
}

class ExportHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(): List<ListeningRecord> = repository.exportRecords()
}

class ImportHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(records: List<ListeningRecord>) = repository.importRecords(records)
}

class ReconcileHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(liveSongs: List<Song>) = repository.reconcile(liveSongs)
}

class PropagateRetagToHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(songs: List<Song>) = repository.propagateRetag(songs)
}

class MergeHistoryUseCase(private val repository: ListeningHistoryRepository) {
    suspend operator fun invoke(deletedSongId: Long, target: Song) =
        repository.mergeInto(deletedSongId, target)
}
