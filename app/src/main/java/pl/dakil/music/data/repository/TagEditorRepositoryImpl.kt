package pl.dakil.music.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
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
    }

    override suspend fun writeTags(songs: List<Song>, newTags: TagEdit): TagWriteResult =
        writeTags(songs.map { SongTagEdit(it, newTags) })

    override suspend fun writeTags(edits: List<SongTagEdit>): TagWriteResult =
        withContext(Dispatchers.IO) {
            val effective = edits.filterNot { it.edit.isEmpty }
            if (effective.isEmpty()) return@withContext TagWriteResult.Success

            try {
                val scannedPaths = ArrayList<String>(effective.size)
                effective.forEach { (song, edit) ->
                    writeOne(song, edit)
                    filePath(song.uri)?.let(scannedPaths::add)
                }
                // Writing the bytes schedules an async MediaStore rescan that overwrites
                // our index update. Force it and *wait* so a subsequent library query
                // reflects every edit — otherwise the last-written file races and is missed.
                awaitMediaScan(scannedPaths)
                TagWriteResult.Success
            } catch (security: SecurityException) {
                recoverIntentSender(effective.map { it.song.uri }, security)
                    ?.let { TagWriteResult.RequiresPermission(it) }
                    ?: TagWriteResult.Error(security)
            } catch (t: Throwable) {
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
        val temp = File.createTempFile("tag_", ".${fileExtension(song)}", context.cacheDir)
        try {
            // 1. Pull the file into a private working copy JAudiotagger can read by path.
            resolver.openInputStream(song.uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open ${song.uri}")

            // 2. Rewrite the embedded tags in place on the copy.
            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tagOrCreateAndSetDefault
            edit.title?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            edit.album?.let { tag.setField(FieldKey.ALBUM, it) }
            edit.genre?.let { tag.setField(FieldKey.GENRE, it) }
            edit.year?.let { tag.setField(FieldKey.YEAR, it) }
            edit.trackNumber?.let { tag.setField(FieldKey.TRACK, it) }
            audioFile.commit()

            // 3. Stream the rewritten bytes back over the original file (needs write grant).
            resolver.openOutputStream(song.uri, "wt")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot write ${song.uri}")

            // 4. Mirror the changes into the MediaStore index for instant UI consistency.
            updateIndex(song.uri, edit)
        } finally {
            temp.delete()
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
