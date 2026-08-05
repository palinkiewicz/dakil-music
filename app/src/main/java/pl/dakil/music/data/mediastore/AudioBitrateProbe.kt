package pl.dakil.music.data.mediastore

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Resolves the *actual* bitrate of an audio file.
 *
 * `size * 8 / duration` is only correct for raw PCM: for a compressed file it also counts
 * the tag block — an embedded cover alone routinely adds hundreds of kbps to a short
 * track — and for a lossless container it says nothing about the codec. So the file is
 * probed instead, cheapest-first, and the arithmetic estimate is kept only as a last
 * resort:
 *
 * 1. **JAudiotagger's audio header** (requires a readable path). The most accurate source:
 *    every reader measures the audio *stream* only, skipping the metadata blocks, and MP3
 *    VBR is taken from the Xing/VBRI header rather than the first frame. Headers only —
 *    it does not read the audio, so it stays fast even on large FLACs.
 * 2. **[MediaExtractor]'s `KEY_BIT_RATE`** on the audio track — the stream bitrate as the
 *    platform parser sees it, and works off a content Uri when there is no usable path
 *    (external files opened from another app). Many parsers simply omit the key.
 * 3. **[MediaMetadataRetriever]'s `METADATA_KEY_BITRATE`** — the container-level value,
 *    the same one MediaStore's `BITRATE` column is derived from.
 *
 * Every step is guarded: probing is best-effort on untrusted files, and a failure just
 * falls through to the next source.
 */
class AudioBitrateProbe(private val context: Context) {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        TagOptionSingleton.getInstance().isAndroid = true
    }

    /**
     * Returns the bitrate of [uri] in bits per second, or 0 when it cannot be determined.
     * [path] is the file's filesystem path when MediaStore knows one; [fallbackBps] is used
     * when no probe yields a value (typically MediaStore's own column, else 0).
     */
    fun bitrateBps(uri: Uri, path: String?, fallbackBps: Int): Int =
        fromAudioHeader(path)
            ?: fromExtractor(uri)
            ?: fromRetriever(uri)
            ?: fallbackBps

    private fun fromAudioHeader(path: String?): Int? = runCatching {
        val file = path?.let(::File) ?: return@runCatching null
        if (!file.canRead()) return@runCatching null
        // bitRateAsNumber is kbps; for VBR files it is the average over the stream.
        val kbps = AudioFileIO.read(file).audioHeader.bitRateAsNumber
        (kbps * 1000).toInt().takeIf { it > 0 }
    }.getOrNull()

    private fun fromExtractor(uri: Uri): Int? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                if (!format.containsKey(MediaFormat.KEY_BIT_RATE)) continue
                return@runCatching format.getInteger(MediaFormat.KEY_BIT_RATE).takeIf { it > 0 }
            }
            null
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun fromRetriever(uri: Uri): Int? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
    }.getOrNull()
}
