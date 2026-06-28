package pl.dakil.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song

/**
 * Loads a cover-art bitmap for the home-screen widget, mirroring the in-app
 * resolution: a song flagged for individual art prefers its embedded picture,
 * otherwise the album's shared art. Falls back across both sources.
 *
 * The shared art is read with [android.content.ContentResolver.loadThumbnail] on the
 * song's own MediaStore uri (reliable on API 29+, unlike opening the deprecated
 * `albumart` uri). Results are capped at [TARGET_PX]: bitmaps travel through
 * RemoteViews, which has a tight transaction size limit.
 */
object WidgetArtLoader {

    private const val TARGET_PX = 512

    suspend fun load(context: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        val sources = if (song.individualCoverArt) {
            listOf({ embedded(context, song) }, { thumbnail(context, song) })
        } else {
            listOf({ thumbnail(context, song) }, { embedded(context, song) })
        }
        sources.firstNotNullOfOrNull { it() }
    }

    private fun thumbnail(context: Context, song: Song): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(song.uri, Size(TARGET_PX, TARGET_PX), null)
    }.getOrNull()

    private fun embedded(context: Context, song: Song): Bitmap? = runCatching {
        val bytes = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, song.uri)
            retriever.embeddedPicture
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > TARGET_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}
