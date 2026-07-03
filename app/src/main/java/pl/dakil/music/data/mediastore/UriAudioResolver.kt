package pl.dakil.music.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.ExternalAudioResolver
import pl.dakil.music.domain.util.ArtistSplitter

/**
 * Resolves an externally supplied audio [Uri] (ACTION_VIEW from a file manager or
 * SAF picker) into a [Song]. Library-first: a MediaStore-backed match reuses the
 * library's own [Song] (and the app's permanent read access to it); anything else —
 * voice notes, downloads not indexed as music — becomes a synthetic [Song] built
 * from the file's own metadata, playable only through the granted [Uri].
 */
class UriAudioResolver(private val context: Context) : ExternalAudioResolver {

    override suspend fun resolve(uri: Uri, librarySongs: List<Song>): Song =
        withContext(Dispatchers.IO) {
            matchLibrary(uri, librarySongs) ?: buildSynthetic(uri)
        }

    private fun matchLibrary(uri: Uri, songs: List<Song>): Song? {
        matchByMediaStoreId(uri, songs)?.let { return it }
        // SAF / file-manager uris don't expose the MediaStore id; a DISPLAY_NAME +
        // SIZE pair is a strong enough fingerprint to link back to the library row.
        val (name, size) = queryNameAndSize(uri)
        if (name == null || size == null) return null
        val id = findMediaStoreIdByNameAndSize(name, size) ?: return null
        return songs.find { it.id == id }
    }

    private fun buildSynthetic(uri: Uri): Song {
        val (displayName, _) = queryNameAndSize(uri)
        val meta = runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                RetrievedMeta(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L,
                )
            }
        }.getOrNull()
        val rawArtist = meta?.artist.orEmpty()
        return Song(
            id = syntheticIdFor(uri),
            uri = uri,
            title = meta?.title?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')
                ?: uri.lastPathSegment.orEmpty(),
            artists = ArtistSplitter.split(rawArtist),
            rawArtist = rawArtist,
            album = meta?.album.orEmpty(),
            albumId = 0L,
            albumArtUri = null,
            durationMs = meta?.durationMs ?: 0L,
            trackNumber = 0,
            year = 0,
            genre = "",
            mimeType = context.contentResolver.getType(uri) ?: "audio/*",
            dateAddedSeconds = 0L,
            // Coil's EmbeddedArtFetcher reads the picture straight from this uri.
            individualCoverArt = true,
        )
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        if (uri.scheme != "content") {
            return uri.lastPathSegment to null
        }
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) {
                        cursor.getLong(sizeCol)
                    } else {
                        null
                    }
                    return name to size
                }
            }
        }
        return null to null
    }

    private fun findMediaStoreIdByNameAndSize(name: String, size: Long): Long? {
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Audio.Media.SIZE} = ?"
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                selection,
                arrayOf(name, size.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        }
        return null
    }

    private data class RetrievedMeta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
    )

    companion object {
        /**
         * Matches a `content://media/…` uri against the library by its embedded _ID.
         * Static and resolver-free so it is unit-testable on the JVM.
         */
        fun matchByMediaStoreId(uri: Uri, songs: List<Song>): Song? {
            if (uri.authority != MediaStore.AUTHORITY) return null
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
            return songs.find { it.id == id }
        }

        /**
         * Deterministic negative id for non-library songs: never collides with a
         * MediaStore _ID (those are positive) and stays numeric so
         * MediaItemMapper's mediaId round-trip keeps working.
         */
        fun syntheticIdFor(uri: Uri): Long = syntheticIdFor(uri.toString())

        /** String overload keeps the id derivation pure and JVM-testable. */
        fun syntheticIdFor(uriString: String): Long =
            -(uriString.hashCode().toLong() and 0x7FFF_FFFFL) - 1L
    }
}
