package pl.dakil.music.data.repository

import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import pl.dakil.music.data.lyrics.LrclibDataSource
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.Lyrics
import pl.dakil.music.domain.model.LyricsSource
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.LyricsRepository
import pl.dakil.music.domain.util.LrcParser
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads embedded lyrics with JAudiotagger (MediaStore metadata is often incomplete)
 * and delegates online lookups to [LrclibDataSource].
 *
 * Reading mirrors the proven approach in [TagEditorRepositoryImpl]: copy the
 * content Uri to a private cache file JAudiotagger can open by path, sniffing the
 * real container from the magic bytes when the name lies. Only the read path is
 * needed here (no MP4 healing/rewriting), so it is kept self-contained.
 */
class LyricsRepositoryImpl(
    private val context: Context,
    private val lrclib: LrclibDataSource,
) : LyricsRepository {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        TagOptionSingleton.getInstance().isAndroid = true
    }

    private val workDir: File by lazy {
        runCatching { context.cacheDir.canonicalFile }.getOrDefault(context.cacheDir)
    }

    override suspend fun readFromMetadata(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val temp = runCatching { copyToTemp(song) }.getOrNull() ?: return@withContext null
        try {
            val audioFile = runCatching { AudioFileIO.read(temp) }.getOrNull() ?: return@withContext null
            val tag = audioFile.tag ?: return@withContext null
            val raw = runCatching { tag.getFirst(FieldKey.LYRICS) }.getOrNull()
            if (raw.isNullOrBlank()) return@withContext null
            val source = if (LrcParser.isSynced(raw)) {
                LyricsSource.METADATA_SYNCED
            } else {
                LyricsSource.METADATA_PLAIN
            }
            LrcParser.build(raw, source).takeIf { it.lines.isNotEmpty() }
        } finally {
            temp.delete()
        }
    }

    override suspend fun searchLrclib(artist: String, track: String): List<LrclibMatch> =
        lrclib.search(artist, track)

    private fun copyToTemp(song: Song): File {
        var temp = File.createTempFile("lyr_", ".${fileExtension(song)}", workDir)
        context.contentResolver.openInputStream(song.uri)?.use { input ->
            temp.outputStream().use { input.copyTo(it) }
        } ?: error("Cannot open ${song.uri}")
        // Re-point at the real container when the name disagrees with the bytes.
        sniffExtension(temp)?.let { real ->
            if (!temp.name.endsWith(".$real", ignoreCase = true)) {
                val retyped = File.createTempFile("lyr_", ".$real", workDir)
                temp.copyTo(retyped, overwrite = true)
                temp.delete()
                temp = retyped
            }
        }
        return temp
    }

    private fun sniffExtension(file: File): String? = runCatching {
        val head = ByteArray(12)
        val read = file.inputStream().use { it.read(head) }
        if (read < 8) return@runCatching null
        fun matches(offset: Int, magic: String) =
            magic.indices.all { offset + it < read && head[offset + it] == magic[it].code.toByte() }
        when {
            matches(4, "ftyp") -> "m4a"
            matches(0, "fLaC") -> "flac"
            matches(0, "OggS") -> "ogg"
            matches(0, "ID3") -> "mp3"
            head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            else -> null
        }
    }.getOrNull()

    private fun fileExtension(song: Song): String {
        context.contentResolver
            .query(song.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0).orEmpty()
                    val dot = name.lastIndexOf('.')
                    if (dot in 0 until name.length - 1) return name.substring(dot + 1)
                }
            }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(song.mimeType) ?: "mp3"
    }
}
