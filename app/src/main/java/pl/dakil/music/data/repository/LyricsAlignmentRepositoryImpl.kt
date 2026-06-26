package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.repository.LyricsAlignmentRepository

/** Stores per-song synced-lyrics offsets in a dedicated Preferences DataStore. */
class LyricsAlignmentRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : LyricsAlignmentRepository {

    override fun offsetMs(contentKey: String): Flow<Long> =
        dataStore.data.map { it[longPreferencesKey(contentKey)] ?: 0L }

    override suspend fun setOffsetMs(contentKey: String, offsetMs: Long) {
        dataStore.edit {
            if (offsetMs == 0L) it.remove(longPreferencesKey(contentKey))
            else it[longPreferencesKey(contentKey)] = offsetMs
        }
    }

    override suspend fun clear(contentKey: String) {
        dataStore.edit { it.remove(longPreferencesKey(contentKey)) }
    }
}
