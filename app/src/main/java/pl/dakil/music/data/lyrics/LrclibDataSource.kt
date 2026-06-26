package pl.dakil.music.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import pl.dakil.music.domain.model.LrclibMatch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Calls the lrclib.net search endpoint with a plain [HttpURLConnection] and parses
 * the JSON array with `org.json` (no extra dependencies). Any failure yields an
 * empty list so the feature degrades gracefully when offline.
 *
 * DNS resolution on Android is flaky on the first attempt under some setups
 * (notably strict Private DNS / DoT), so a [UnknownHostException] is retried a few
 * times with a short backoff before giving up.
 */
class LrclibDataSource {

    suspend fun search(artist: String, track: String): List<LrclibMatch> =
        withContext(Dispatchers.IO) {
            if (track.isBlank()) return@withContext emptyList()
            val url = URL("$BASE_URL?artist_name=${encode(artist)}&track_name=${encode(track)}")
            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    return@withContext request(url)
                } catch (e: UnknownHostException) {
                    Log.w("Lrclib", "DNS failure (attempt ${attempt + 1}/$MAX_ATTEMPTS) for '$artist' / '$track'", e)
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                } catch (e: Exception) {
                    Log.w("Lrclib", "search failed for '$artist' / '$track'", e)
                    return@withContext emptyList()
                }
            }
            emptyList()
        }

    private fun request(url: URL): List<LrclibMatch> {
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(body: String): List<LrclibMatch> {
        val array = JSONArray(body)
        val out = ArrayList<LrclibMatch>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            out.add(
                LrclibMatch(
                    id = o.optLong("id"),
                    trackName = o.optString("trackName"),
                    artistName = o.optString("artistName"),
                    albumName = o.optString("albumName"),
                    durationSec = o.optDouble("duration", 0.0),
                    plainLyrics = if (o.isNull("plainLyrics")) null else o.optString("plainLyrics"),
                    syncedLyrics = if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics"),
                ),
            )
        }
        return out
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val BASE_URL = "https://lrclib.net/api/search"
        const val TIMEOUT_MS = 10_000
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 800L
        const val USER_AGENT = "pl.dakil.music (Android music player)"
    }
}
