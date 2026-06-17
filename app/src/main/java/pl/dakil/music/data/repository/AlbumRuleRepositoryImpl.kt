package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AlbumRule
import pl.dakil.music.domain.repository.AlbumRuleRepository

/**
 * Stores per-album rules as a JSON document inside a single DataStore key, mirroring
 * [UserPlaylistRepositoryImpl]. Enum overrides are persisted by name; an absent name
 * means "no override". Upserting an empty rule deletes it.
 */
class AlbumRuleRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : AlbumRuleRepository {

    override val rules: Flow<List<AlbumRule>> =
        dataStore.data.map { decode(it[KEY]) }

    override suspend fun upsert(rule: AlbumRule) {
        update { current ->
            val others = current.filterNot { it.albumKey == rule.albumKey }
            if (rule.isEmpty) others else others + rule
        }
    }

    override suspend fun delete(albumKey: String) {
        update { current -> current.filterNot { it.albumKey == albumKey } }
    }

    private suspend fun update(transform: (List<AlbumRule>) -> List<AlbumRule>) {
        dataStore.edit { prefs ->
            prefs[KEY] = encode(transform(decode(prefs[KEY])))
        }
    }

    private fun encode(rules: List<AlbumRule>): String {
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject().put("albumKey", rule.albumKey)
            rule.coverArtMode?.let { obj.put("coverArtMode", it.name) }
            rule.authorMode?.let { obj.put("authorMode", it.name) }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<AlbumRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                AlbumRule(
                    albumKey = obj.getString("albumKey"),
                    coverArtMode = obj.optString("coverArtMode").takeIf { it.isNotEmpty() }
                        ?.let { name -> AlbumCoverArtMode.entries.firstOrNull { it.name == name } },
                    authorMode = obj.optString("authorMode").takeIf { it.isNotEmpty() }
                        ?.let { name -> AlbumAuthorMode.entries.firstOrNull { it.name == name } },
                ).takeUnless { it.isEmpty }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("album_rules_json")
    }
}
