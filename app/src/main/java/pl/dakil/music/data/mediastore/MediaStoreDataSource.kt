package pl.dakil.music.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
        )

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
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                songs.ensureCapacity(cursor.count)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val rawArtist = cursor.getString(artistCol).orEmptyArtist()

                    songs += Song(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = cursor.getString(titleCol) ?: "",
                        artists = ArtistSplitter.split(rawArtist),
                        rawArtist = rawArtist,
                        album = cursor.getString(albumCol).orEmptyAlbum(),
                        albumId = albumId,
                        albumArtUri = albumArtUri(albumId),
                        durationMs = cursor.getLong(durationCol),
                        // TRACK is encoded as DDDTTT (disc*1000 + track); keep just the track part.
                        trackNumber = (cursor.getInt(trackCol) % 1000),
                        mimeType = cursor.getString(mimeCol) ?: "audio/*",
                        dateAddedSeconds = cursor.getLong(dateCol),
                    )
                }
            }

        songs
    }

    private fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)

    private fun String?.orEmptyArtist() =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) "" else this

    private fun String?.orEmptyAlbum() =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) "" else this

    private companion object {
        val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
