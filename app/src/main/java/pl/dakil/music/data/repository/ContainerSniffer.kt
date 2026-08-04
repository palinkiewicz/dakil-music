package pl.dakil.music.data.repository

import java.io.File
import java.io.RandomAccessFile

/**
 * Detects an audio file's real container from its magic bytes, independent of its
 * (possibly wrong) name, returning the extension JAudiotagger should use to pick a
 * reader.
 *
 * Getting this wrong is destructive rather than merely wrong. JAudiotagger chooses
 * its reader *and writer* purely from the extension, and the MP3 writer pads its tag
 * out to the first MPEG frame sync it can find, zeroing everything before it. FLAC
 * audio frames begin with `0xFFF8`, which satisfies the MPEG sync mask — so a FLAC
 * handed to the MP3 writer loses its entire audio stream and keeps its file size,
 * which is exactly what a leading ID3v2 tag used to cause here.
 */
internal object ContainerSniffer {

    private const val ID3_HEADER_BYTES = 10
    private const val ID3_FOOTER_BYTES = 10

    /** Fixed part of an Ogg page header; the segment table follows it. */
    private const val OGG_HEADER_BYTES = 27
    private const val OGG_SEGMENT_COUNT_OFFSET = 26

    /** The extension for [file]'s actual container, or null for unrecognized bytes. */
    fun extensionOf(file: File): String? = runCatching {
        val head = head(file) ?: return@runCatching null
        when {
            head.startsWith(4, "ftyp") -> "m4a" // ISO base media: MP4 / M4A (incl. fragmented/DASH)
            head.startsWith(0, "fLaC") -> "flac"
            head.startsWith(0, "OggS") -> insideOgg(file, 0)
            // An ID3v2 tag says nothing about what follows it — EAC and other rippers
            // prepend one to FLAC and Ogg too — so only the bytes past the tag decide.
            head.startsWith(0, "ID3") -> behindId3(file, head) ?: "mp3"
            head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 -> "mp3" // MPEG sync
            else -> null
        }
    }.getOrNull()

    /** The container that starts where [head]'s ID3v2 tag ends, or null if unrecognized. */
    private fun behindId3(file: File, head: ByteArray): String? {
        if (head.size < ID3_HEADER_BYTES) return null
        var size = 0
        for (i in 6 until ID3_HEADER_BYTES) {
            val byte = head[i].toInt()
            // A syncsafe integer never sets the high bit. One that does means these are
            // not the bytes we think they are, so leave the guess to the caller.
            if (byte and 0x80 != 0) return null
            size = (size shl 7) or (byte and 0x7F)
        }
        // Bit 4 of the flags byte marks a 10-byte footer trailing the tag.
        val footer = if (head[5].toInt() and 0x10 != 0) ID3_FOOTER_BYTES else 0
        val start = ID3_HEADER_BYTES + size.toLong() + footer
        val magic = readAt(file, start, 4) ?: return null
        return when {
            magic.startsWith(0, "fLaC") -> "flac"
            magic.startsWith(0, "OggS") -> insideOgg(file, start)
            else -> null
        }
    }

    /**
     * Which codec rides in the Ogg stream starting at [start].
     *
     * "Ogg" is a container, not a format, and JAudiotagger needs to know which: it has a
     * working Opus reader/writer, but reaches it only through the `.opus` extension. Call
     * an Opus file "ogg" and it goes to the Vorbis reader, which rejects it with "no
     * vorbiscomment header" — so a `.opus` file would be unrewritable purely because we
     * mislabelled it.
     *
     * The first packet sits right after the page header: 27 fixed bytes plus one byte per
     * segment, and it opens with the codec's own magic.
     */
    private fun insideOgg(file: File, start: Long): String {
        val segments = readAt(file, start + OGG_SEGMENT_COUNT_OFFSET, 1)
            ?.get(0)?.toInt()?.and(0xFF)
            ?: return "ogg"
        val packet = readAt(file, start + OGG_HEADER_BYTES + segments, 8) ?: return "ogg"
        // Anything else — Ogg FLAC, Speex, Theora — keeps the plain Ogg handling it had.
        return if (packet.startsWith(0, "OpusHead")) "opus" else "ogg"
    }

    /** Up to 12 leading bytes, or null when the file is too short to identify at all. */
    private fun head(file: File): ByteArray? = RandomAccessFile(file, "r").use { raf ->
        val count = minOf(12L, raf.length()).toInt()
        if (count < 8) return null
        ByteArray(count).also(raf::readFully)
    }

    /** [count] bytes at [offset], or null when the file ends before them. */
    private fun readAt(file: File, offset: Long, count: Int): ByteArray? =
        RandomAccessFile(file, "r").use { raf ->
            if (offset < 0 || raf.length() < offset + count) return null
            raf.seek(offset)
            ByteArray(count).also(raf::readFully)
        }

    private fun ByteArray.startsWith(offset: Int, magic: String): Boolean =
        magic.indices.all { offset + it < size && this[offset + it] == magic[it].code.toByte() }
}
