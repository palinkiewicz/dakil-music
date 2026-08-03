package pl.dakil.music.domain.util

/**
 * Undoes "mojibake" in tag text: UTF-8 bytes that some reader decoded as a legacy
 * single-byte codepage, so `ę` (`c4 99`) arrives as `Ä™`.
 *
 * This is not a hypothetical. Android's native media extractor runs an ICU charset
 * *guess* over MP4/ID3 tag text and, on a non-UTF-8 locale, occasionally decides a
 * short, perfectly valid UTF-8 string is really the locale's codepage. The file on
 * disk is untouched and spec-correct — only MediaStore's index (and anything else
 * built on the same extractor, including `MediaMetadataRetriever`) carries the
 * damage. It misfires on a handful of rows while identical neighbouring files scan
 * fine, so the fix has to be per-string, not a global setting.
 *
 * The mangling is losslessly reversible: re-encode the text with the codepage that
 * produced it, then decode those bytes as UTF-8. [repair] tries each candidate
 * codepage and accepts one only if the text encodes *fully* into it and the bytes
 * are *strictly* valid UTF-8 — no overlongs, surrogates or stray continuation
 * bytes. Both steps are injective, so a match is a genuine round trip rather than a
 * plausible-looking guess, which is what keeps correctly-scanned text safe: real
 * Polish like `DWADZIEŚCIASIEDEM.` re-encodes to bytes that are not valid UTF-8, so
 * no candidate ever matches it.
 *
 * The codepage tables are inlined rather than resolved through `Charset.forName` so
 * this stays a pure, Android-free domain utility with no per-device charset
 * availability to worry about.
 */
object MojibakeRepair {

    /**
     * The mojibake-corrected form of [text], or [text] unchanged when it is not
     * mojibake — which is the overwhelmingly common case.
     */
    fun repair(text: String): String {
        // Every byte of a multi-byte UTF-8 sequence is >= 0x80, and each of these
        // codepages maps those to non-ASCII characters. Pure ASCII therefore cannot
        // be mojibake, and this skips almost the entire library in one pass.
        if (text.isEmpty() || text.all { it.code < 0x80 }) return text

        for (table in TABLES) {
            val bytes = encode(text, table) ?: continue
            val decoded = decodeStrictUtf8(bytes) ?: continue
            // A repaired tag should read as text; control characters mean the bytes
            // happened to be valid UTF-8 without being the string we want.
            if (decoded.any { it.isControlCharacter() }) continue
            return decoded
        }
        return text
    }

    /**
     * [text] as single-byte codepage values, or null if any character is absent from
     * [table] — in which case [text] cannot have come from that codepage at all.
     */
    private fun encode(text: String, table: CodePage): ByteArray? {
        val out = ByteArray(text.length)
        for (i in text.indices) {
            val c = text[i]
            val byte = if (c.code < 0x80) c.code else table.byteOf(c) ?: return null
            out[i] = byte.toByte()
        }
        return out
    }

    /**
     * [bytes] decoded as UTF-8, or null if they are not *strictly* valid UTF-8.
     * Strictness is the whole point: a lenient decoder that substitutes U+FFFD would
     * happily "repair" text that was never mojibake.
     */
    private fun decodeStrictUtf8(bytes: ByteArray): String? {
        val out = StringBuilder(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val lead = bytes[i].toInt() and 0xFF
            val extra: Int
            var code: Int
            when {
                lead < 0x80 -> { extra = 0; code = lead }
                lead in 0xC2..0xDF -> { extra = 1; code = lead and 0x1F }
                lead in 0xE0..0xEF -> { extra = 2; code = lead and 0x0F }
                lead in 0xF0..0xF4 -> { extra = 3; code = lead and 0x07 }
                // 0x80..0xC1 covers stray continuation bytes and overlong two-byte
                // forms; 0xF5..0xFF is beyond U+10FFFF.
                else -> return null
            }
            if (i + extra >= bytes.size) return null
            for (n in 1..extra) {
                val cont = bytes[i + n].toInt() and 0xFF
                if (cont !in 0x80..0xBF) return null
                code = (code shl 6) or (cont and 0x3F)
            }
            val isOverlong = (extra == 2 && code < 0x800) || (extra == 3 && code < 0x10000)
            if (isOverlong) return null
            // Surrogate halves are not encodable in UTF-8.
            if (code in 0xD800..0xDFFF) return null
            if (code > 0x10FFFF) return null
            out.appendCodePoint(code)
            i += extra + 1
        }
        return out.toString()
    }

    /** C0 and C1 control characters — never legitimate tag text. */
    private fun Char.isControlCharacter(): Boolean = code < 0x20 || code in 0x7F..0x9F

    private fun StringBuilder.appendCodePoint(code: Int) {
        if (code <= 0xFFFF) {
            append(code.toChar())
        } else {
            val v = code - 0x10000
            append((0xD800 + (v shr 10)).toChar())
            append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }

    /**
     * A single-byte codepage's 0x80..0xFF half. [high] holds the character for each
     * byte in order; [UNDEFINED] marks a byte the codepage leaves undefined.
     * The byte -> character mapping is injective, so the reverse lookup is exact.
     */
    private class CodePage(high: String) {
        private val reverse: Map<Char, Int> = buildMap {
            high.forEachIndexed { index, c -> if (c != UNDEFINED) put(c, 0x80 + index) }
        }

        fun byteOf(c: Char): Int? = reverse[c]
    }

    private const val UNDEFINED = '\uFFFF'

    // Generated from the Unicode consortium's mappings; kept as escapes so the
    // tables survive any re-encoding of this source file.
    private const val WINDOWS_1250 =
        "\u20AC\uFFFF\u201A\uFFFF\u201E\u2026\u2020\u2021\uFFFF\u2030\u0160\u2039\u015A\u0164\u017D\u0179" +
            "\uFFFF\u2018\u2019\u201C\u201D\u2022\u2013\u2014\uFFFF\u2122\u0161\u203A\u015B\u0165\u017E\u017A" +
            "\u00A0\u02C7\u02D8\u0141\u00A4\u0104\u00A6\u00A7\u00A8\u00A9\u015E\u00AB\u00AC\u00AD\u00AE\u017B" +
            "\u00B0\u00B1\u02DB\u0142\u00B4\u00B5\u00B6\u00B7\u00B8\u0105\u015F\u00BB\u013D\u02DD\u013E\u017C" +
            "\u0154\u00C1\u00C2\u0102\u00C4\u0139\u0106\u00C7\u010C\u00C9\u0118\u00CB\u011A\u00CD\u00CE\u010E" +
            "\u0110\u0143\u0147\u00D3\u00D4\u0150\u00D6\u00D7\u0158\u016E\u00DA\u0170\u00DC\u00DD\u0162\u00DF" +
            "\u0155\u00E1\u00E2\u0103\u00E4\u013A\u0107\u00E7\u010D\u00E9\u0119\u00EB\u011B\u00ED\u00EE\u010F" +
            "\u0111\u0144\u0148\u00F3\u00F4\u0151\u00F6\u00F7\u0159\u016F\u00FA\u0171\u00FC\u00FD\u0163\u02D9"

    private const val WINDOWS_1252 =
        "\u20AC\uFFFF\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\uFFFF\u017D\uFFFF" +
            "\uFFFF\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\uFFFF\u017E\u0178" +
            "\u00A0\u00A1\u00A2\u00A3\u00A4\u00A5\u00A6\u00A7\u00A8\u00A9\u00AA\u00AB\u00AC\u00AD\u00AE\u00AF" +
            "\u00B0\u00B1\u00B2\u00B3\u00B4\u00B5\u00B6\u00B7\u00B8\u00B9\u00BA\u00BB\u00BC\u00BD\u00BE\u00BF" +
            "\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF" +
            "\u00D0\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D7\u00D8\u00D9\u00DA\u00DB\u00DC\u00DD\u00DE\u00DF" +
            "\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF" +
            "\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6\u00F7\u00F8\u00F9\u00FA\u00FB\u00FC\u00FD\u00FE\u00FF"

    private const val WINDOWS_1251 =
        "\u0402\u0403\u201A\u0453\u201E\u2026\u2020\u2021\u20AC\u2030\u0409\u2039\u040A\u040C\u040B\u040F" +
            "\u0452\u2018\u2019\u201C\u201D\u2022\u2013\u2014\uFFFF\u2122\u0459\u203A\u045A\u045C\u045B\u045F" +
            "\u00A0\u040E\u045E\u0408\u00A4\u0490\u00A6\u00A7\u0401\u00A9\u0404\u00AB\u00AC\u00AD\u00AE\u0407" +
            "\u00B0\u00B1\u0406\u0456\u0491\u00B5\u00B6\u00B7\u0451\u2116\u0454\u00BB\u0458\u0405\u0455\u0457" +
            "\u0410\u0411\u0412\u0413\u0414\u0415\u0416\u0417\u0418\u0419\u041A\u041B\u041C\u041D\u041E\u041F" +
            "\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042A\u042B\u042C\u042D\u042E\u042F" +
            "\u0430\u0431\u0432\u0433\u0434\u0435\u0436\u0437\u0438\u0439\u043A\u043B\u043C\u043D\u043E\u043F" +
            "\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044A\u044B\u044C\u044D\u044E\u044F"

    /**
     * Candidates in order. The order rarely decides anything — a string that encodes
     * into two of these usually encodes to the same bytes in both — and the strict
     * UTF-8 check rejects wrong candidates outright, so this is a tie-break, not a
     * guess. Central European first, since that is where the extractor's locale
     * heuristic is observed to misfire.
     */
    private val TABLES = listOf(
        CodePage(WINDOWS_1250),
        CodePage(WINDOWS_1252),
        CodePage(WINDOWS_1251),
    )
}
