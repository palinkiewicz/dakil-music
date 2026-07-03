package pl.dakil.music.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Snapshot of the play queue persisted for system playback resumption. */
data class ResumptionState(
    val queueIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/**
 * Persists the last real play queue in a dedicated Preferences DataStore so
 * [PlaybackService] can serve `onPlaybackResumption` after process death —
 * System UI and OEM media panels (e.g. Huawei's Control Panel) revive the
 * session with a play command and expect the previous queue back.
 */
class PlaybackResumptionStore(
    private val dataStore: DataStore<Preferences>,
) {

    /** No-op for an empty queue: closing playback must not erase the last real queue. */
    suspend fun save(state: ResumptionState) {
        if (state.queueIds.isEmpty()) return
        val (ids, index) = windowAround(state.queueIds, state.currentIndex)
        dataStore.edit {
            it[QUEUE_IDS] = ids.joinToString(",")
            it[CURRENT_INDEX] = index
            it[CURRENT_MEDIA_ID] = ids[index]
            it[POSITION_MS] = state.positionMs.coerceAtLeast(0)
            it[SHUFFLE] = state.shuffle
            it[REPEAT_MODE] = state.repeatMode
        }
    }

    /** Returns the saved queue, or null when absent or unreadable. */
    suspend fun read(): ResumptionState? = runCatching {
        val prefs = dataStore.data.first()
        val ids = prefs[QUEUE_IDS]
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf(List<Long>::isNotEmpty)
            ?: return null
        var index = (prefs[CURRENT_INDEX] ?: 0).coerceIn(0, ids.lastIndex)
        // Guard against index drift between writes: trust the saved media id.
        prefs[CURRENT_MEDIA_ID]?.let { mediaId ->
            if (ids[index] != mediaId) {
                val actual = ids.indexOf(mediaId)
                if (actual >= 0) index = actual
            }
        }
        ResumptionState(
            queueIds = ids,
            currentIndex = index,
            positionMs = (prefs[POSITION_MS] ?: 0).coerceAtLeast(0),
            shuffle = prefs[SHUFFLE] ?: false,
            repeatMode = prefs[REPEAT_MODE] ?: 0,
        )
    }.getOrNull()

    /** Forgets the saved queue, removing the app from resumption surfaces. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        /** Upper bound on persisted ids; ~4 KB of text keeps the store trivial to rewrite. */
        internal const val MAX_PERSISTED_ITEMS = 500

        /** How much history before the current item survives the windowing. */
        internal const val ITEMS_BEFORE_CURRENT = 100

        /**
         * Caps huge queues to a [MAX_PERSISTED_ITEMS]-wide window around [index],
         * keeping up to [ITEMS_BEFORE_CURRENT] already-played items. Returns the
         * window and the index remapped into it.
         */
        internal fun windowAround(ids: List<Long>, index: Int): Pair<List<Long>, Int> {
            if (ids.isEmpty()) return ids to 0
            val safeIndex = index.coerceIn(0, ids.lastIndex)
            if (ids.size <= MAX_PERSISTED_ITEMS) return ids to safeIndex
            val start = (safeIndex - ITEMS_BEFORE_CURRENT)
                .coerceIn(0, ids.size - MAX_PERSISTED_ITEMS)
            return ids.subList(start, start + MAX_PERSISTED_ITEMS).toList() to (safeIndex - start)
        }

        private val QUEUE_IDS = stringPreferencesKey("queue_ids")
        private val CURRENT_INDEX = intPreferencesKey("current_index")
        private val CURRENT_MEDIA_ID = longPreferencesKey("current_media_id")
        private val POSITION_MS = longPreferencesKey("position_ms")
        private val SHUFFLE = booleanPreferencesKey("shuffle")
        private val REPEAT_MODE = intPreferencesKey("repeat_mode")
    }
}
