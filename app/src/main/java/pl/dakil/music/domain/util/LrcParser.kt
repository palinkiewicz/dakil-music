package pl.dakil.music.domain.util

import pl.dakil.music.domain.model.LyricLine
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LyricsSource

/**
 * Parses and serializes LRC-format lyrics. Pure and Android-free so it can be
 * unit-tested in isolation.
 *
 * An LRC line looks like `[mm:ss.xx]text`; a line may carry several timestamps
 * (for repeated lyrics) — each becomes its own [LyricLine]. Plain lyrics (no
 * timestamps) map to lines with a null time.
 */
object LrcParser {

    // Accepts [mm:ss], [mm:ss.xx] and [mm:ss.xxx] (and ':' as the fraction separator).
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** True if [text] contains at least one LRC timestamp. */
    fun isSynced(text: String): Boolean = TIMESTAMP.containsMatchIn(text)

    /**
     * Builds a [Lyrics] from raw text, auto-detecting synced vs plain. [source]
     * records provenance; it does not affect parsing.
     */
    fun build(text: String, source: LyricsSource): Lyrics {
        val synced = isSynced(text)
        val lines = if (synced) parse(text) else parsePlain(text)
        return Lyrics(
            lines = lines,
            synced = synced && lines.any { it.timeMs != null },
            plainText = if (synced) stripTimestamps(text) else text.trim(),
            source = source,
        )
    }

    /** Parses LRC text into time-ordered lines (timed lines first, then any plain trailers). */
    fun parse(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        text.lineSequence().forEach { raw ->
            val matches = TIMESTAMP.findAll(raw).toList()
            if (matches.isEmpty()) {
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty()) out.add(LyricLine(null, trimmed))
            } else {
                val content = raw.substring(matches.last().range.last + 1).trim()
                matches.forEach { out.add(LyricLine(toMs(it), content)) }
            }
        }
        return out.sortedWith(compareBy(nullsLast()) { it.timeMs })
    }

    /** Splits plain lyrics into lines, preserving blank lines as verse separators. */
    private fun parsePlain(text: String): List<LyricLine> =
        text.trim().lineSequence().map { LyricLine(null, it.trim()) }.toList()

    /** Removes every timestamp, returning the bare text (one line per source line). */
    fun stripTimestamps(text: String): String =
        text.lineSequence()
            .map { it.replace(TIMESTAMP, "").trim() }
            .joinToString("\n")
            .trim()

    /**
     * Serializes timed [lines] back to LRC, shifting every timestamp by [offsetMs]
     * (negative allowed; clamped at 0). Plain lines are dropped.
     */
    fun serialize(lines: List<LyricLine>, offsetMs: Long = 0L): String =
        lines.asSequence()
            .filter { it.timeMs != null }
            .joinToString("\n") { line ->
                "[${formatTs((line.timeMs!! + offsetMs).coerceAtLeast(0L))}]${line.text}"
            }

    /**
     * Index of the line that should be highlighted at [positionMs], or -1 before
     * the first timed line. Assumes [lines] are time-ordered (timed first).
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var index = -1
        for (i in lines.indices) {
            val time = lines[i].timeMs ?: break
            if (time <= positionMs) index = i else break
        }
        return index
    }

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return (min * 60 + sec) * 1000 + fracMs
    }

    private fun formatTs(ms: Long): String {
        val totalCs = ms / 10
        val cs = totalCs % 100
        val totalSec = totalCs / 100
        val sec = totalSec % 60
        val min = totalSec / 60
        return "%02d:%02d.%02d".format(min, sec, cs)
    }
}
