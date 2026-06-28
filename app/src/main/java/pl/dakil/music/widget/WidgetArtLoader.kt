package pl.dakil.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song

/**
 * Loads a small cover-art bitmap for the home-screen widget. Mirrors the in-app
 * cover-art resolution: a song flagged for individual art uses its embedded picture,
 * otherwise the album's shared MediaStore art; either falls back to the other.
 *
 * Bitmaps pushed into a widget travel through RemoteViews, which has a tight
 * transaction size limit, so the result is always downscaled to [TARGET_PX].
 */
object WidgetArtLoader {

    private const val TARGET_PX = 256

    suspend fun load(context: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        val preferEmbedded = song.individualCoverArt
        val first = if (preferEmbedded) embedded(context, song) else album(context, song)
        first ?: if (preferEmbedded) album(context, song) else embedded(context, song)
    }

    private fun embedded(context: Context, song: Song): Bitmap? = runCatching {
        val bytes = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, song.uri)
            retriever.embeddedPicture
        } ?: return null
        decodeScaled { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
    }.getOrNull()

    private fun album(context: Context, song: Song): Bitmap? {
        val uri = song.albumArtUri ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                decodeScaled { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
            }
        }.getOrNull()
    }

    /** Two-pass decode: measure bounds, pick an integer sample size, then decode downscaled. */
    private inline fun decodeScaled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > TARGET_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return decode(opts)
    }
}
