package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.domain.repository.UserPlaylistRepository
import java.util.UUID

/**
 * Stores user playlists as a JSON document inside a single DataStore key. JSON
 * (via the built-in [org.json] API) keeps the schema flexible without pulling in a
 * serialization plugin, and avoids delimiter issues with arbitrary playlist names.
 */
class UserPlaylistRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : UserPlaylistRepository {

    override val playlists: Flow<List<UserPlaylist>> =
        dataStore.data.map { decode(it[KEY]) }

    override suspend fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        update { current -> current + UserPlaylist(id, name.trim(), emptyList()) }
        return id
    }

    override suspend fun rename(id: String, newName: String) {
        update { current ->
            current.map { if (it.id == id) it.copy(name = newName.trim()) else it }
        }
    }

    override suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun addSongs(id: String, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        update { current ->
            current.map { playlist ->
                if (playlist.id != id) {
                    playlist
                } else {
                    val existing = playlist.songIds.toHashSet()
                    val appended = songIds.filter { it !in existing }
                    playlist.copy(songIds = playlist.songIds + appended)
                }
            }
        }
    }

    private suspend fun update(transform: (List<UserPlaylist>) -> List<UserPlaylist>) {
        dataStore.edit { prefs ->
            prefs[KEY] = encode(transform(decode(prefs[KEY])))
        }
    }

    private fun encode(playlists: List<UserPlaylist>): String {
        val array = JSONArray()
        for (playlist in playlists) {
            val ids = JSONArray().apply { playlist.songIds.forEach { put(it) } }
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("songIds", ids),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<UserPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val idsArray = obj.optJSONArray("songIds") ?: JSONArray()
                UserPlaylist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    songIds = (0 until idsArray.length()).map { idsArray.getLong(it) },
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("user_playlists_json")
    }
}
