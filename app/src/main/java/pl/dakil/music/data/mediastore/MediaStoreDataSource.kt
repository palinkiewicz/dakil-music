package pl.dakil.music.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.util.ArtistSplitter

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
        // MediaStore's YEAR column is unreliable on some devices/formats (often 0 even
        // when the file is tagged). We fall back to reading the embedded tag, but only
        // for rows MediaStore left at 0, reusing one retriever to bound the cost.
        var retriever: MediaMetadataRetriever? = null

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

                    var year = cursor.getInt(yearCol)
                    if (year <= 0) {
                        if (retriever == null) retriever = MediaMetadataRetriever()
                        year = readEmbeddedYear(retriever, uri)
                    }

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
                        year = year,
                        genre = if (genreCol >= 0) cursor.getString(genreCol).orEmpty() else "",
                        mimeType = cursor.getString(mimeCol) ?: "audio/*",
                        dateAddedSeconds = cursor.getLong(dateCol),
                    )
                }
            }

        retriever?.release()
        songs
    }

    /** Reads the embedded year/date tag from a file; 0 when absent or unreadable. */
    private fun readEmbeddedYear(retriever: MediaMetadataRetriever, uri: Uri): Int = try {
        retriever.setDataSource(context, uri)
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        parseYear(year) ?: parseYear(date) ?: 0
    } catch (_: Throwable) {
        0
    }

    /** Extracts the first 4-digit year from a tag value like "2019" or "2019-05-01". */
    private fun parseYear(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return YEAR_REGEX.find(value)?.value?.toIntOrNull()
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
        val YEAR_REGEX = Regex("""\d{4}""")
        val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        val UNKNOWN_MARKERS = setOf(
            "<unknown>",
            "unknown",
            "unknown album",
            "unknown artist",
        )
    }
}
