package pl.dakil.music.data.coverart

import android.content.Context
import coil.imageLoader
import pl.dakil.music.domain.model.Song

/**
 * Invalidates cached cover art after it has been re-written into song files, so the
 * UI drops stale bitmaps immediately. Clears both the per-song embedded-art entry and
 * the album's shared MediaStore art entry from Coil's disk cache, and flushes the
 * (opaque-keyed) memory cache — a cheap, rare operation.
 */
class CoverArtRefresher(private val context: Context) {

    fun invalidate(songs: List<Song>) {
        if (songs.isEmpty()) return
        val loader = context.imageLoader
        val disk = loader.diskCache
        for (song in songs) {
            disk?.remove(song.uri.toString())
            disk?.remove("${song.uri}#0")
            song.albumArtUri?.let { disk?.remove(it.toString()) }
        }
        loader.memoryCache?.clear()
    }
}
