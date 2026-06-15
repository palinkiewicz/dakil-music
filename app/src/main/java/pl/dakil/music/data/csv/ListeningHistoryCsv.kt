package pl.dakil.music.data.csv

import androidx.core.net.toUri
import pl.dakil.music.domain.model.ListeningRecord
import pl.dakil.music.domain.util.ContentKey

/**
 * CSV (RFC-4180-ish) serialization for listening history import/export.
 *
 * One record per line. Fields containing a comma or quote are quoted, with inner
 * quotes doubled; embedded line breaks are flattened to spaces on write so the
 * file stays line-addressable for import. `artists` is joined with `;`.
 * `contentKey` is not stored — it is re-derived on import.
 */
object ListeningHistoryCsv {

    private val COLUMNS = listOf(
        "startTimestamp", "songId", "secondsPlayed", "timesPlayed",
        "title", "artists", "album", "albumId", "albumArtUri", "durationMs",
    )

    val header: String = COLUMNS.joinToString(",")

    /** True when [line] is the exact expected header (case-insensitive, trimmed). */
    fun isValidHeader(line: String): Boolean =
        parseLine(line).map { it.trim() } == COLUMNS

    fun toRow(record: ListeningRecord): String = listOf(
        record.startTimestamp.toString(),
        record.songId.toString(),
        record.secondsPlayed.toString(),
        record.timesPlayed.toString(),
        escape(record.title),
        escape(record.artists.joinToString(";")),
        escape(record.album),
        record.albumId.toString(),
        escape(record.albumArtUri?.toString().orEmpty()),
        record.durationMs.toString(),
    ).joinToString(",")

    /** Parses one data row, or null if it is malformed (caller counts the skip). */
    fun parseRow(line: String): ListeningRecord? {
        if (line.isBlank()) return null
        val f = parseLine(line)
        if (f.size != COLUMNS.size) return null
        val startTimestamp = f[0].toLongOrNull() ?: return null
        val songId = f[1].toLongOrNull() ?: return null
        val secondsPlayed = f[2].toIntOrNull() ?: return null
        val timesPlayed = f[3].toIntOrNull() ?: return null
        val title = f[4]
        val artists = f[5].split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val album = f[6]
        val albumId = f[7].toLongOrNull() ?: return null
        val albumArtUri = f[8].takeIf { it.isNotBlank() }?.toUri()
        val durationMs = f[9].toLongOrNull() ?: return null

        if (startTimestamp <= 0L || secondsPlayed < 0 || timesPlayed < 1) return null

        return ListeningRecord(
            songId = songId,
            startTimestamp = startTimestamp,
            secondsPlayed = secondsPlayed,
            timesPlayed = timesPlayed,
            title = title,
            artists = artists,
            album = album,
            albumId = albumId,
            albumArtUri = albumArtUri,
            durationMs = durationMs,
            contentKey = ContentKey.of(title, artists, album),
        )
    }

    private fun escape(value: String): String {
        val flat = value.replace('\n', ' ').replace('\r', ' ')
        return if (flat.contains(',') || flat.contains('"')) {
            "\"" + flat.replace("\"", "\"\"") + "\""
        } else {
            flat
        }
    }

    /** Splits a single CSV line into fields, honoring quoting and doubled quotes. */
    private fun parseLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    out.add(sb.toString()); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
