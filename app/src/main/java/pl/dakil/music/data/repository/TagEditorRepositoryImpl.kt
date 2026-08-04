package pl.dakil.music.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.TagOptionSingleton
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.TagWriteResult
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume

/**
 * Writes embedded ID3/Vorbis/MP4 tags into the actual file bytes using JAudiotagger.
 *
 * Scoped Storage forbids opening another app's media for writing without consent, so
 * the strategy is: copy the content to a private cache file, rewrite its tags there,
 * then stream the bytes back over the original [Uri] (which requires the write grant).
 * A [SecurityException] is converted into an [IntentSender] the UI launches to obtain
 * that grant, after which the whole batch is retried.
 */
class TagEditorRepositoryImpl(
    private val context: Context,
) : TagEditorRepository {

    init {
        // JAudiotagger is noisy on the default logger; quiet it down.
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        // Use the Android image handler so artwork is read/written with Bitmap, not
        // the desktop javax.imageio path (which is absent on Android and would throw).
        TagOptionSingleton.getInstance().isAndroid = true
        // Container-level options are shared with every other JAudiotagger caller in the
        // process (the lyrics reader included), so put them in force from the start.
        TagFileWriter.applyGlobalOptions()
    }

    /**
     * Cache dir with symlinks resolved. When an MP4/M4A cover edit grows the `moov`
     * atom, JAudiotagger rewrites the file by embedding `getCanonicalPath()` of the
     * working file as a data reference and *reopening that path by string* to re-read
     * the samples. `context.cacheDir` is `/data/user/0/<pkg>/cache`, whose canonical
     * form (`/data/data/...`) can't be reopened on some devices — yielding ENOENT.
     * Creating the working file under the canonical dir makes the round-trip path valid.
     */
    private val workDir: File by lazy {
        runCatching { context.cacheDir.canonicalFile }.getOrDefault(context.cacheDir)
    }

    override suspend fun writeTags(songs: List<Song>, newTags: TagEdit): TagWriteResult =
        writeTags(songs.map { SongTagEdit(it, newTags) })

    override suspend fun writeTags(edits: List<SongTagEdit>): TagWriteResult =
        withContext(Dispatchers.IO) {
            val effective = edits.filterNot { it.edit.isEmpty }
            if (effective.isEmpty()) return@withContext TagWriteResult.Success

            try {
                val written = ArrayList<SongTagEdit>(effective.size)
                val scannedPaths = ArrayList<String>(effective.size)
                var skipped = 0
                effective.forEach { entry ->
                    try {
                        writeOne(entry.song, entry.edit)
                        written += entry
                        filePath(entry.song.uri)?.let(scannedPaths::add)
                    } catch (skip: UnsupportedFileException) {
                        // A file we must not (or cannot) rewrite without destroying it.
                        // Leave it untouched and keep going so the rest of the batch saves.
                        Log.w("TagEditor", "Skipping ${entry.song.uri}: ${skip.message}")
                        skipped++
                    }
                }
                // Writing the bytes schedules an async MediaStore rescan. Force it and
                // *wait* so a subsequent library query reflects every edit — otherwise the
                // last-written file races and is missed.
                awaitMediaScan(scannedPaths)
                // Only now mirror the edits into the index: a scan recomputes every column
                // from the file, so anything written before it is discarded. Doing it after
                // also covers the fields the extractor will not surface at all, without the
                // app ever having to read embedded tags at query time.
                written.forEach { (song, edit) -> updateIndex(song.uri, edit) }
                // Every requested file was unsafe to write — surface that as a failure.
                if (scannedPaths.isEmpty() && skipped > 0) {
                    TagWriteResult.Error(UnsupportedFileException("No file could be written"))
                } else {
                    TagWriteResult.Success
                }
            } catch (security: SecurityException) {
                recoverIntentSender(effective.map { it.song.uri }, security)
                    ?.let { TagWriteResult.RequiresPermission(it) }
                    ?: TagWriteResult.Error(security)
            } catch (t: Throwable) {
                Log.e("TagEditor", "writeTags failed", t)
                TagWriteResult.Error(t)
            }
        }

    /** Scans [paths] and suspends until MediaStore has finished indexing them all. */
    private suspend fun awaitMediaScan(paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && !continuation.isCompleted) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    /** Resolves the absolute file path MediaScanner needs; null if unavailable. */
    private fun filePath(uri: Uri): String? {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return null
    }

    private fun writeOne(song: Song, edit: TagEdit) {
        val resolver = context.contentResolver
        val temp = File.createTempFile("tag_", ".${fileExtension(song)}", workDir)
        var written: File? = null
        try {
            // 1. Pull the file into a private working copy JAudiotagger can read by path.
            resolver.openInputStream(song.uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open ${song.uri}")

            // 2. Rewrite the tags on the copy. This may hand back a *different* file
            // (a copy whose name misstated its container has to be renamed first), and
            // it throws rather than return a file whose rewrite went wrong.
            written = TagFileWriter.write(temp, edit, workDir)

            // 3. Stream the rewritten bytes back over the original file (needs write grant).
            resolver.openOutputStream(song.uri, "wt")?.use { out ->
                written.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot write ${song.uri}")
        } finally {
            temp.delete()
            if (written != null && written != temp) written.delete()
        }
    }

    private fun updateIndex(uri: Uri, edit: TagEdit) {
        val values = ContentValues().apply {
            edit.title?.let { put(MediaStore.Audio.Media.TITLE, it) }
            edit.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            edit.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            edit.year?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
            edit.trackNumber?.toIntOrNull()?.let { put(MediaStore.Audio.Media.TRACK, it) }
        }
        if (values.size() > 0) {
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    /** Best-effort extension lookup so JAudiotagger can detect the container format. */
    private fun fileExtension(song: Song): String {
        val resolver = context.contentResolver
        resolver.query(song.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0).orEmpty()
                    val dot = name.lastIndexOf('.')
                    if (dot in 0 until name.length - 1) return name.substring(dot + 1)
                }
            }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(song.mimeType) ?: "mp3"
    }

    private fun recoverIntentSender(uris: List<Uri>, security: SecurityException): IntentSender? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createWriteRequest(context.contentResolver, uris).intentSender

            security is RecoverableSecurityException ->
                security.userAction.actionIntent.intentSender

            else -> null
        }
}
