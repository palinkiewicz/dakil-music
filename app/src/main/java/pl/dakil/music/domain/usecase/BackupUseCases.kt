package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.model.BackupCategory
import pl.dakil.music.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream

class ExportBackupCategoryUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(category: BackupCategory, out: OutputStream) =
        repository.exportCategory(category, out)
}

class ImportBackupCategoryUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(category: BackupCategory, input: InputStream) =
        repository.importCategory(category, input)
}

class ExportFullBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(out: OutputStream) = repository.exportFull(out)
}

class ImportFullBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(input: InputStream) = repository.importFull(input)
}
