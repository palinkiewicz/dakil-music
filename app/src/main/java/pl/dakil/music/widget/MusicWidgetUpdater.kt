package pl.dakil.music.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer

/**
 * Keeps placed [MusicWidget]s current. On every song change it decodes the new
 * track's cover art (honoring the album-vs-individual cover setting, like Now Playing)
 * into [WidgetArtHolder] and then forces a Glance refresh, so art appears immediately.
 *
 * Play/pause, favorites and metadata are observed live inside the widget composition,
 * so this reacts only to the song identity — not the 500ms position ticks.
 */
object MusicWidgetUpdater {

    fun start(context: Context, container: AppContainer, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            container.playerRepository.playbackState
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { song ->
                    val art = song?.let { WidgetArt.load(appContext, container, it) }
                    WidgetArtHolder.set(song?.id, art)
                    runCatching { MusicWidget().updateAll(appContext) }
                }
        }
    }
}
