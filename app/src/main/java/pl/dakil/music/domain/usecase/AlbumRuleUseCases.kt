package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AlbumRule
import pl.dakil.music.domain.repository.AlbumRuleRepository

class ObserveAlbumRulesUseCase(private val repository: AlbumRuleRepository) {
    operator fun invoke(): Flow<List<AlbumRule>> = repository.rules
}

class UpsertAlbumRuleUseCase(private val repository: AlbumRuleRepository) {
    suspend operator fun invoke(rule: AlbumRule) = repository.upsert(rule)
}

class DeleteAlbumRuleUseCase(private val repository: AlbumRuleRepository) {
    suspend operator fun invoke(albumKey: String) = repository.delete(albumKey)
}
