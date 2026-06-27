package pl.dakil.music.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SongFileInfo
import pl.dakil.music.domain.util.ArtistSplitter
import androidx.core.net.toUri

/**
 * Single source of truth for raw audio data. Runs the MediaStore query off the
 * main thread and maps each row into a [Song], splitting performers as it goes.
 *
 * Works for any container MediaStore indexes as audio (mp3, opus, m4a, flac, …);
 * the [MediaStore.Audio.Media.IS_MUSIC] filter keeps out ringtones/notifications.
 */
class MediaStoreDataSource(private val context: Context) {

    suspend fun queryAudio(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // GENRE as a media column only exists from API 30 (R).
        val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (hasGenreColumn) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        // Only real music tracks with a positive duration (skips 0-length stubs).
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = ArrayList<Song>()

        context.contentResolver.query(collection, projection, selection, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val genreCol = if (hasGenreColumn) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else {
                    -1
                }

                songs.ensureCapacity(cursor.count)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val rawArtist = cursor.getString(artistCol).orEmptyArtist()
                    val uri = ContentUris.withAppendedId(collection, id)


                    songs += Song(
                        id = id,
                        uri = uri,
                        title = cursor.getString(titleCol) ?: "",
                        artists = ArtistSplitter.split(rawArtist),
                        rawArtist = rawArtist,
                        album = cursor.getString(albumCol).orEmptyAlbum(),
                        albumId = albumId,
                        albumArtUri = albumArtUri(albumId),
                        durationMs = cursor.getLong(durationCol),
                        // TRACK is encoded as DDDTTT (disc*1000 + track); keep just the track part.
                        trackNumber = (cursor.getInt(trackCol) % 1000),
                        year = cursor.getInt(yearCol),
                        genre = if (genreCol >= 0) cursor.getString(genreCol).orEmpty() else "",
                        mimeType = cursor.getString(mimeCol) ?: "audio/*",
                        dateAddedSeconds = cursor.getLong(dateCol),
                    )
                }
            }

        songs
    }

    /**
     * Queries MediaStore for the filesystem details of [songs]. [MediaStore.Audio.Media.BITRATE]
     * only exists from API 30 (R); below that — or when MediaStore reports no value — the
     * bitrate is estimated from the file size and duration.
     */
    suspend fun fileInfo(songs: List<Song>): List<SongFileInfo> = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext emptyList()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val hasBitrateColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.SIZE)
            if (hasBitrateColumn) add(MediaStore.Audio.Media.BITRATE)
        }.toTypedArray()

        val songById = songs.associateBy { it.id }
        val ids = songById.keys
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${MediaStore.Audio.Media._ID} IN ($placeholders)"
        val args = ids.map { it.toString() }.toTypedArray()

        val result = ArrayList<SongFileInfo>(songs.size)

        context.contentResolver.query(collection, projection, selection, args, null)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val bitrateCol = if (hasBitrateColumn) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val song = songById[cursor.getLong(idCol)] ?: continue
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val durationSeconds = song.durationMs / 1000.0
                    val bitrate = when {
                        bitrateCol >= 0 && !cursor.isNull(bitrateCol) -> cursor.getInt(bitrateCol)
                        durationSeconds > 0 -> (size * 8 / durationSeconds).toInt()
                        else -> 0
                    }
                    result += SongFileInfo(
                        song = song,
                        path = path,
                        sizeBytes = size,
                        durationMs = song.durationMs,
                        bitrateBps = bitrate,
                        format = formatLabel(path, song.mimeType),
                    )
                }
            }

        result
    }

    /** Short, user-facing format label, preferring the file extension over the MIME subtype. */
    private fun formatLabel(path: String?, mimeType: String): String {
        val ext = path?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
        if (ext != null) return ext.uppercase()
        return mimeType.substringAfterLast('/', "").substringAfterLast('.')
            .ifBlank { mimeType }
            .uppercase()
    }

    private fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)

    private fun String?.orEmptyArtist() = if (isUnknownTag()) "" else this!!

    private fun String?.orEmptyAlbum() = if (isUnknownTag()) "" else this!!

    /**
     * True when MediaStore is signalling "no value". Besides the canonical
     * [MediaStore.UNKNOWN_STRING] ("<unknown>"), several OEMs substitute a localized
     * or plain "unknown" label, which we also treat as missing so such tracks fall
     * into the synthetic "No album" / unknown-artist buckets.
     */
    private fun String?.isUnknownTag(): Boolean {
        if (isNullOrBlank()) return true
        val normalized = trim().lowercase()
        return normalized == MediaStore.UNKNOWN_STRING ||
            normalized in UNKNOWN_MARKERS
    }

    private companion object {
        val ALBUM_ART_BASE_URI: Uri = "content://media/external/audio/albumart".toUri()
        val UNKNOWN_MARKERS = setOf(
            "<unknown>",
            "unknown",
            "unknown album",
            "unknown artist",
        )
    }
}
