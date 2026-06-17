package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AlbumRule

/** Persists per-album overrides of the global cover-art / author settings. */
interface AlbumRuleRepository {

    val rules: Flow<List<AlbumRule>>

    /** Inserts or replaces the rule for [AlbumRule.albumKey]; deletes it if empty. */
    suspend fun upsert(rule: AlbumRule)

    suspend fun delete(albumKey: String)
}
