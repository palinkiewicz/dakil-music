package pl.dakil.music.data.coverart

import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

/**
 * Coil model requesting the *embedded* cover art of the audio file at [songUri].
 * Unlike the MediaStore per-album art URI, this reads the picture baked into the
 * individual track. [version] lets a refresh bust the cache for the same file.
 */
data class EmbeddedArt(val songUri: Uri, val version: Int = 0)

/**
 * Extracts a track's embedded picture via [MediaMetadataRetriever]. Runs on the IO
 * dispatcher (Coil already calls fetchers off the main thread); Coil's memory + disk
 * cache then serves repeat loads so extraction is a one-time cost per file.
 */
class EmbeddedArtFetcher(
    private val data: EmbeddedArt,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bytes = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(options.context, data.songUri)
            retriever.embeddedPicture
        } ?: return@withContext null
        SourceResult(
            source = ImageSource(Buffer().apply { write(bytes) }, options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<EmbeddedArt> {
        override fun create(data: EmbeddedArt, options: Options, imageLoader: ImageLoader): Fetcher =
            EmbeddedArtFetcher(data, options)
    }
}

/** Stable Coil cache key for an [EmbeddedArt] request. */
class EmbeddedArtKeyer : Keyer<EmbeddedArt> {
    override fun key(data: EmbeddedArt, options: Options): String = "${data.songUri}#${data.version}"
}
