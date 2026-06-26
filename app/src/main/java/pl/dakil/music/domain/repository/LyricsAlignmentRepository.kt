package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persists a per-song synced-lyrics time offset (in milliseconds), keyed by the
 * song's [pl.dakil.music.domain.util.ContentKey]. A positive offset delays the
 * lyrics; a negative one advances them.
 */
interface LyricsAlignmentRepository {

    /** Observes the saved offset for [contentKey]; emits 0 when none is set. */
    fun offsetMs(contentKey: String): Flow<Long>

    suspend fun setOffsetMs(contentKey: String, offsetMs: Long)

    /** Removes any saved offset for [contentKey] (used after burning into metadata). */
    suspend fun clear(contentKey: String)
}
