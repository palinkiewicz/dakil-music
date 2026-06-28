package pl.dakil.music.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * Type-preserving, schema-agnostic serialization of a Preferences DataStore to text,
 * so any of the app's preference stores (settings, favorites, playlists, …) can be
 * backed up and restored without a bespoke serializer each.
 *
 * Format: one entry per line, `type,key,value`. The type tag records the original
 * Preferences value type so it round-trips exactly. The value is the rest of the line
 * (keys never contain a comma); newlines are flattened so every entry stays on one
 * line. String sets are stored as a JSON array. Unknown types/lines are skipped.
 */
object PreferencesBackupCodec {

    suspend fun export(store: DataStore<Preferences>): String {
        val prefs = store.data.first()
        val sb = StringBuilder()
        for ((key, value) in prefs.asMap()) {
            val (tag, raw) = encode(value) ?: continue
            sb.append(tag).append(',').append(key.name).append(',')
                .append(raw.replace('\n', ' ').replace('\r', ' ')).append('\n')
        }
        return sb.toString()
    }

    /**
     * Replaces all of [store]'s contents with the entries parsed from [text].
     *
     * Validates the *contents* rather than any file name: text with real lines but no
     * recognizable entry is rejected with [IllegalArgumentException] so a wrong or
     * corrupt file can't silently wipe the store. Genuinely empty text is accepted and
     * clears the store (an empty category is a valid backup).
     */
    suspend fun import(store: DataStore<Preferences>, text: String) {
        val nonBlankLines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val entries = nonBlankLines.mapNotNull(::parseLine)
        require(nonBlankLines.isEmpty() || entries.isNotEmpty()) {
            "Not a valid backup file: no recognizable entries"
        }
        store.edit { prefs ->
            prefs.clear()
            for ((tag, name, raw) in entries) {
                runCatching { apply(prefs, tag, name, raw) }
            }
        }
    }

    private fun encode(value: Any?): Pair<String, String>? = when (value) {
        is Boolean -> "B" to value.toString()
        is Int -> "I" to value.toString()
        is Long -> "L" to value.toString()
        is Float -> "F" to value.toString()
        is Double -> "D" to value.toString()
        is String -> "S" to value
        is Set<*> -> "SS" to JSONArray(value.map { it.toString() }).toString()
        else -> null
    }

    private data class Entry(val tag: String, val name: String, val value: String)

    private val KNOWN_TAGS = setOf("B", "I", "L", "F", "D", "S", "SS")

    /** Parses one `type,key,value` line, or null when it isn't a recognizable entry. */
    private fun parseLine(line: String): Entry? {
        val first = line.indexOf(',')
        if (first <= 0) return null
        val second = line.indexOf(',', first + 1)
        if (second < 0) return null
        val tag = line.substring(0, first)
        if (tag !in KNOWN_TAGS) return null
        return Entry(
            tag = tag,
            name = line.substring(first + 1, second),
            value = line.substring(second + 1),
        )
    }

    private fun apply(prefs: androidx.datastore.preferences.core.MutablePreferences, tag: String, name: String, raw: String) {
        when (tag) {
            "B" -> prefs[booleanPreferencesKey(name)] = raw.toBoolean()
            "I" -> prefs[intPreferencesKey(name)] = raw.toInt()
            "L" -> prefs[longPreferencesKey(name)] = raw.toLong()
            "F" -> prefs[floatPreferencesKey(name)] = raw.toFloat()
            "D" -> prefs[doublePreferencesKey(name)] = raw.toDouble()
            "S" -> prefs[stringPreferencesKey(name)] = raw
            "SS" -> prefs[stringSetPreferencesKey(name)] = jsonToSet(raw)
        }
    }

    private fun jsonToSet(raw: String): Set<String> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapTo(LinkedHashSet()) { array.getString(it) }
    }
}
