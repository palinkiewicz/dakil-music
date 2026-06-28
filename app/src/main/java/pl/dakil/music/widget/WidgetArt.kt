package pl.dakil.music.widget

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.first
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.Song

/**
 * Resolves cover art for a playback [Song] the same way the rest of the app does.
 *
 * The playback queue carries plain songs without the per-album cover-art decision, so
 * we look the song up in [pl.dakil.music.domain.repository.MusicRepository.annotatedSongs]
 * (which folds in the album cover-art setting and any per-album rule) and use that
 * annotated copy's [Song.individualCoverArt] flag when decoding.
 */
object WidgetArt {

    suspend fun load(context: Context, container: AppContainer, song: Song): Bitmap? {
        val annotated = runCatching {
            container.musicRepository.annotatedSongs.first().firstOrNull { it.id == song.id }
        }.getOrNull()
        return WidgetArtLoader.load(context, annotated ?: song)
    }
}
