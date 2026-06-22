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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.audio.mp4.Flatten
import org.jcodec.containers.mp4.MP4Util
import org.jcodec.containers.mp4.boxes.Box
import org.jcodec.containers.mp4.boxes.DataRefBox
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.UrlBox
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.TagWriteResult
import java.io.File
import java.io.RandomAccessFile
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

    private companion object {
        /** Scheme JCodec uses for absolute MP4 data references (see `Flatten.resolveDataRef`). */
        const val FILE_URL_PREFIX = "file://"
    }

    init {
        // JAudiotagger is noisy on the default logger; quiet it down.
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        // Use the Android image handler so artwork is read/written with Bitmap, not
        // the desktop javax.imageio path (which is absent on Android and would throw).
        TagOptionSingleton.getInstance().isAndroid = true
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
                val scannedPaths = ArrayList<String>(effective.size)
                var skipped = 0
                effective.forEach { (song, edit) ->
                    try {
                        writeOne(song, edit)
                        filePath(song.uri)?.let(scannedPaths::add)
                    } catch (skip: UnsupportedFileException) {
                        // A file we must not (or cannot) rewrite without destroying it.
                        // Leave it untouched and keep going so the rest of the batch saves.
                        Log.w("TagEditor", "Skipping ${song.uri}: ${skip.message}")
                        skipped++
                    }
                }
                // Writing the bytes schedules an async MediaStore rescan that overwrites
                // our index update. Force it and *wait* so a subsequent library query
                // reflects every edit — otherwise the last-written file races and is missed.
                awaitMediaScan(scannedPaths)
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
        var temp = File.createTempFile("tag_", ".${fileExtension(song)}", workDir)
        val placeholders = ArrayList<File>()
        try {
            // 1. Pull the file into a private working copy JAudiotagger can read by path.
            resolver.openInputStream(song.uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open ${song.uri}")

            // 1a. The display name can lie about the container — e.g. a real MP4/M4A file
            // saved with a ".mp3" name. JAudiotagger picks its reader purely from the file
            // extension, so a misnamed file gets the wrong reader and fails (an MP4 read as
            // MP3 throws "No audio header found"). Re-detect from the actual magic bytes and
            // re-point the working copy at the right extension when the two disagree.
            sniffExtension(temp)?.let { real ->
                if (!temp.name.endsWith(".$real", ignoreCase = true)) {
                    val retyped = File.createTempFile("tag_", ".$real", workDir)
                    temp.copyTo(retyped, overwrite = true)
                    temp.delete()
                    temp = retyped
                }
            }

            // 1b. Repair stale MP4 data references left by earlier edits (see helper).
            placeholders.addAll(materializeStaleDataRefs(temp))

            // 1c. JCodec's MP4 rewrite keeps only the last track, so editing an
            // audio+text/video M4A would silently drop its audio. Reduce such files to
            // their single audio track up front (the extra track is discarded) so the
            // rewrite — and every future edit — is safe (see helper).
            if (trackCount(temp) > 1) {
                try {
                    reduceToSingleAudioTrack(temp)
                } catch (e: Exception) {
                    // Never write a half-reduced file back over the original.
                    throw UnsupportedFileException("Could not reduce multi-track file: ${e.message}")
                }
            }

            // 2. Rewrite the embedded tags in place on the copy.
            val audioFile = try {
                AudioFileIO.read(temp)
            } catch (e: Exception) {
                // A file JAudiotagger cannot parse: an already-corrupted file (e.g. a
                // previous edit dropped its audio track) or an unsupported container.
                // Skip it rather than failing the whole batch — note the read can throw
                // exceptions outside the CannotReadException hierarchy (e.g.
                // InvalidAudioFrameException extends Exception directly).
                throw UnsupportedFileException("Unreadable audio file: ${e.message}")
            }
            val tag = audioFile.tagOrCreateAndSetDefault
            edit.title?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            edit.album?.let { tag.setField(FieldKey.ALBUM, it) }
            edit.genre?.let { tag.setField(FieldKey.GENRE, it) }
            edit.year?.let { tag.setField(FieldKey.YEAR, it) }
            edit.trackNumber?.let { tag.setField(FieldKey.TRACK, it) }
            edit.artwork?.let { art ->
                tag.deleteArtworkField()
                val artwork = AndroidArtwork().apply {
                    binaryData = art.bytes
                    mimeType = art.mimeType
                    pictureType = PictureTypes.DEFAULT_ID
                    // Prime width/height from the bytes so FLAC/Vorbis pictures are valid.
                    setImageFromData()
                }
                tag.setField(artwork)
            }
            audioFile.commit()

            // 3. Stream the rewritten bytes back over the original file (needs write grant).
            resolver.openOutputStream(song.uri, "wt")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot write ${song.uri}")

            // 4. Mirror the changes into the MediaStore index for instant UI consistency.
            updateIndex(song.uri, edit)
        } finally {
            placeholders.forEach { it.delete() }
            temp.delete()
        }
    }

    /**
     * Heals stale MP4/M4A data references before a rewrite.
     *
     * When a cover/metadata edit grows the `moov` atom, JAudiotagger rewrites the file
     * by *flattening* it, re-reading the audio samples through each track's data
     * reference (`dref`). The flatten embeds the working file's absolute path into that
     * reference and clears its "self-contained" flag — and JCodec's [TrakBox.setDataRef]
     * then refuses to update a non-self-contained reference on later edits. So once a
     * file has been edited, it permanently points its samples at a since-deleted cache
     * temp, and every subsequent edit fails with `FileNotFoundException` (ENOENT).
     *
     * We can't cheaply rewrite the broken reference, but the frozen path is stable and
     * the chunk offsets still describe *this* file's byte layout. So for each referenced
     * file that is missing we drop a byte-identical copy of [working] at that path; the
     * flatten then reads valid sample bytes. Returns the placeholder files to delete
     * after the write. Non-MP4 inputs (and pristine self-contained MP4s) yield nothing.
     */
    /** Number of tracks in [file] if it is an MP4/M4A; 1 for non-MP4 (nothing to reduce). */
    private fun trackCount(file: File): Int = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            MP4Util.parseFullMovieChannel(raf.channel)?.moov?.tracks?.size ?: 1
        }
    }.getOrDefault(1)

    /**
     * Rewrites [working] in place keeping only its first audio track.
     *
     * JCodec's `ReplaceMP4Editor` (used by JAudiotagger's MP4 writer) collapses a moov
     * down to a single track — it removes every `trak`, then re-adds only the last one —
     * so editing a multi-track M4A (e.g. an "Official Video" file carrying audio + a
     * text/subtitle track) would discard the audio. We pre-empt that by flattening the
     * file to just its audio track; the chunk offsets still describe this file's layout,
     * so pointing the track's data reference back at it lets `Flatten` re-read valid
     * samples. The non-audio track is intentionally dropped.
     */
    private fun reduceToSingleAudioTrack(working: File) {
        val movie = RandomAccessFile(working, "r").use { raf ->
            MP4Util.parseFullMovieChannel(raf.channel)
        } ?: return
        val moov = movie.moov ?: return
        val audio = moov.audioTracks.firstOrNull() ?: return // no audio: leave for the read guard
        // Point the audio track's data reference at THIS file so Flatten can re-read its
        // samples. setDataRef() refuses to overwrite an already-external ref (the stale
        // frozen-path case), so reset the dref entries directly.
        val dref = NodeBox.findFirstPath(audio, DataRefBox::class.java, Box.path("mdia.minf.dinf.dref"))
        if (dref != null) {
            dref.removeChildren(arrayOf("url ", "alis"))
            dref.add(UrlBox.createUrlBox(FILE_URL_PREFIX + working.canonicalPath))
        } else {
            audio.setDataRef(FILE_URL_PREFIX + working.canonicalPath)
        }
        // Drop the track reference to the chapter/text track we're about to remove,
        // otherwise players warn about a missing referenced track.
        audio.removeChildren(arrayOf("tref"))
        moov.removeChildren(arrayOf("trak"))
        moov.add(audio)

        val flattened = File.createTempFile("strip_", ".m4a", workDir)
        try {
            Flatten().flatten(movie, flattened)
            flattened.copyTo(working, overwrite = true)
        } finally {
            flattened.delete()
        }
    }

    private fun materializeStaleDataRefs(working: File): List<File> {
        val created = ArrayList<File>()
        runCatching {
            RandomAccessFile(working, "r").use { raf ->
                val movie = MP4Util.parseFullMovieChannel(raf.channel)
                val drefPath = Box.path("mdia.minf.dinf.dref")
                movie?.moov?.tracks?.forEach { trak ->
                    val dref = NodeBox.findFirstPath(trak, DataRefBox::class.java, drefPath)
                        ?: return@forEach
                    dref.boxes.forEach inner@{ box ->
                        val url = (box as? UrlBox)?.url ?: return@inner
                        if (!url.startsWith(FILE_URL_PREFIX)) return@inner
                        val target = File(url.substring(FILE_URL_PREFIX.length))
                        if (!target.exists()) {
                            runCatching {
                                working.copyTo(target, overwrite = true)
                                created.add(target)
                            }
                        }
                    }
                }
            }
        }
        return created
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

    /**
     * Detects the real container from [file]'s leading magic bytes, independent of its
     * (possibly wrong) name, returning the extension JAudiotagger should use to pick a
     * reader. Returns null for unrecognized bytes so the name-based guess stands.
     */
    private fun sniffExtension(file: File): String? = runCatching {
        val head = ByteArray(12)
        val read = file.inputStream().use { it.read(head) }
        if (read < 8) return@runCatching null
        fun matches(offset: Int, magic: String) =
            magic.indices.all { offset + it < read && head[offset + it] == magic[it].code.toByte() }
        when {
            matches(4, "ftyp") -> "m4a"  // ISO base media: MP4 / M4A (incl. fragmented/DASH)
            matches(0, "fLaC") -> "flac"
            matches(0, "OggS") -> "ogg"
            matches(0, "ID3") -> "mp3"
            head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 -> "mp3"  // MPEG sync
            else -> null
        }
    }.getOrNull()

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

/** A file that can't be tag-edited without corrupting it, so the write is skipped. */
private class UnsupportedFileException(message: String) : Exception(message)
