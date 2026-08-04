package pl.dakil.music.data.repository

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp4.Flatten
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.audio.wav.WavSaveOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jcodec.containers.mp4.MP4Util
import org.jcodec.containers.mp4.boxes.Box
import org.jcodec.containers.mp4.boxes.DataRefBox
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.UrlBox
import pl.dakil.music.domain.repository.TagEdit
import java.io.File
import java.io.RandomAccessFile

/**
 * Rewrites the embedded tags of a working copy of an audio file.
 *
 * This is the half of tag editing that touches actual bytes, kept apart from
 * [TagEditorRepositoryImpl] — which owns the Scoped Storage dance around it — so it can
 * be exercised against real files in plain JVM tests. Every container quirk that has
 * cost us a file lives here, and each one has a test under `test/.../tageditor/`, run
 * against real (synthesized) audio in every container the app supports.
 *
 * The caller owns [working]: it must already be a private copy, never the user's file,
 * because a failed rewrite can leave it in any state at all.
 */
internal object TagFileWriter {

    /** Scheme JCodec uses for absolute MP4 data references (see `Flatten.resolveDataRef`). */
    private const val FILE_URL_PREFIX = "file://"

    /**
     * Sets the JAudiotagger-wide options this app depends on.
     *
     * These live on a process-global singleton, so they have to be in force before
     * *anything* reads a tag — the lyrics reader included — or a WAV would be read one
     * way before the first tag edit and another way after. [TagEditorRepositoryImpl]
     * calls this as the app starts; [write] calls it again so JVM tests of this object
     * get the same behaviour. Applying it twice is harmless.
     */
    fun applyGlobalOptions() {
        // A WAV can carry metadata in a RIFF INFO chunk or an ID3 chunk. JAudiotagger
        // defaults to INFO, which supports a handful of fields and *throws* for the rest —
        // so editing lyrics or cover art on a WAV used to abort the whole batch. Reading
        // ID3 makes a WAV behave like an mp3 (every field available), and saving both
        // chunks in sync keeps players that only look at INFO seeing the same values.
        TagOptionSingleton.getInstance().wavOptions = WavOptions.READ_ID3_ONLY_AND_SYNC
        TagOptionSingleton.getInstance().wavSaveOptions = WavSaveOptions.SAVE_BOTH_AND_SYNC
    }

    /**
     * Applies [edit] to [working] and returns the file that now holds the result — which
     * may be a *different* file in [workDir], since a copy whose name misstates its
     * container has to be renamed before JAudiotagger will touch it. The returned file is
     * the one to stream back.
     *
     * Nothing here deletes anything: both [working] and any file returned are the
     * caller's to clean up, so a throw part-way through cannot strand a temp file.
     *
     * Throws [UnsupportedFileException] for a file that cannot be rewritten safely. That
     * is a refusal, not a failure: the caller must leave the user's file alone rather
     * than write back whatever state the attempt left behind.
     */
    fun write(working: File, edit: TagEdit, workDir: File): File {
        applyGlobalOptions()
        var file = working

        // The display name can lie about the container — e.g. a real MP4/M4A file saved
        // with a ".mp3" name. JAudiotagger picks its reader purely from the file
        // extension, so a misnamed file gets the wrong reader and fails (an MP4 read as
        // MP3 throws "No audio header found"). Re-detect from the actual magic bytes and
        // re-point the working copy at the right extension when the two disagree.
        val container = ContainerSniffer.extensionOf(file)
        if (container != null && !file.name.endsWith(".$container", ignoreCase = true)) {
            val retyped = File.createTempFile("tag_", ".$container", workDir)
            file.copyTo(retyped, overwrite = true)
            file = retyped
        }

        // Heal stale MP4 data references frozen in by earlier edits (see helper).
        healStaleDataRefs(file, workDir)

        // JCodec's MP4 rewrite keeps only the last track, so editing an audio+text/video
        // M4A would silently drop its audio. Reduce such files to their single audio
        // track up front (the extra track is discarded) so the rewrite — and every future
        // edit — is safe (see helper).
        if (trackCount(file) > 1) {
            try {
                reduceToSingleAudioTrack(file, workDir)
            } catch (e: Exception) {
                // Never let a half-reduced file be written back over the original.
                throw UnsupportedFileException("Could not reduce multi-track file: ${e.message}")
            }
        }

        applyTags(file, edit)

        // Last line of defence: a rewrite that lost the container it started with
        // destroyed the audio, and streaming it back would overwrite the only good copy
        // the user has. Detection beats trust here — the writer reports success either
        // way — so re-sniff and refuse the file rather than hand back rubble.
        if (container != null && ContainerSniffer.extensionOf(file) != container) {
            throw UnsupportedFileException("Rewrite did not preserve the $container container")
        }
        return file
    }

    private fun applyTags(file: File, edit: TagEdit) {
        val audioFile = try {
            AudioFileIO.read(file)
        } catch (e: Exception) {
            // A file JAudiotagger cannot parse: an already-corrupted file (e.g. a previous
            // edit dropped its audio track) or an unsupported container. Skip it rather
            // than failing the whole batch — note the read can throw exceptions outside
            // the CannotReadException hierarchy (e.g. InvalidAudioFrameException extends
            // Exception directly).
            throw UnsupportedFileException("Unreadable audio file: ${e.message}")
        }
        val tag = audioFile.tagOrCreateAndSetDefault
        edit.title?.let { tag.setField(FieldKey.TITLE, it) }
        edit.artist?.let { tag.setField(FieldKey.ARTIST, it) }
        edit.album?.let { tag.setField(FieldKey.ALBUM, it) }
        edit.genre?.let { tag.setField(FieldKey.GENRE, it) }
        edit.year?.let {
            tag.setField(FieldKey.YEAR, it)
            // The field JAudiotagger picks for YEAR is one the platform extractor does
            // not read on mp3/flac/ogg, so mirror it into one it does.
            ScannerVisibleYear.write(tag, it)
        }
        edit.trackNumber?.let { tag.setField(FieldKey.TRACK, it) }
        // A single LYRICS field per container; prefer the synced LRC (recognized as
        // synced by most players, and still readable as plain) over plain text.
        (edit.syncedLyrics ?: edit.plainLyrics)?.let { tag.setField(FieldKey.LYRICS, it) }
        edit.artwork?.let { art ->
            tag.deleteArtworkField()
            // getNew() honours TagOptionSingleton.isAndroid, so this is a Bitmap-backed
            // artwork on a device and an ImageIO-backed one under a JVM test.
            val artwork = ArtworkFactory.getNew().apply {
                binaryData = art.bytes
                mimeType = art.mimeType
                pictureType = PictureTypes.DEFAULT_ID
                // Prime width/height from the bytes so FLAC/Vorbis pictures are valid.
                setImageFromData()
            }
            tag.setField(artwork)
        }
        audioFile.commit()
    }

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
    private fun reduceToSingleAudioTrack(working: File, workDir: File) {
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

    /**
     * Heals stale MP4/M4A data references frozen in by an earlier edit.
     *
     * When a cover/metadata edit grows the `moov` atom, JAudiotagger rewrites the file by
     * *flattening* it, re-reading the audio samples through each track's data reference
     * (`dref`). The flatten embeds the working file's absolute cache path into that
     * reference and clears its "self-contained" flag. So once a file has been edited it
     * permanently points its samples at a since-deleted cache temp, and every later edit
     * fails with ENOENT when the flatten tries to re-read from that path. Worse, the
     * frozen path can belong to a *different* app (e.g. the `.debug` build), which the
     * release build has no permission to even create a file at — so dropping a placeholder
     * copy there is impossible.
     *
     * The chunk offsets still describe *this* file's byte layout, so we instead repoint
     * each stale `dref` back at [working] itself and re-flatten; the flatten then re-reads
     * valid sample bytes from the working copy. `setDataRef()` refuses to overwrite an
     * already-external ref, so the entries are reset directly. Healthy self-contained MP4s
     * (and non-MP4 inputs) have no stale `file://` ref and are left untouched.
     */
    private fun healStaleDataRefs(working: File, workDir: File) {
        val movie = RandomAccessFile(working, "r").use { raf ->
            MP4Util.parseFullMovieChannel(raf.channel)
        } ?: return
        val moov = movie.moov ?: return
        val drefPath = Box.path("mdia.minf.dinf.dref")
        var healed = false
        moov.tracks.forEach { trak ->
            val dref = NodeBox.findFirstPath(trak, DataRefBox::class.java, drefPath)
                ?: return@forEach
            val stale = dref.boxes.any { box ->
                val url = (box as? UrlBox)?.url ?: return@any false
                url.startsWith(FILE_URL_PREFIX) &&
                    !File(url.substring(FILE_URL_PREFIX.length)).exists()
            }
            if (stale) {
                dref.removeChildren(arrayOf("url ", "alis"))
                dref.add(UrlBox.createUrlBox(FILE_URL_PREFIX + working.canonicalPath))
                healed = true
            }
        }
        if (!healed) return

        val flattened = File.createTempFile("heal_", ".m4a", workDir)
        try {
            Flatten().flatten(movie, flattened)
            flattened.copyTo(working, overwrite = true)
        } finally {
            flattened.delete()
        }
    }
}

/** A file that can't be tag-edited without corrupting it, so the write is skipped. */
internal class UnsupportedFileException(message: String) : Exception(message)
