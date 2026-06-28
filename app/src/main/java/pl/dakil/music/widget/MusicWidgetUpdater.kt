package pl.dakil.music.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import pl.dakil.music.domain.repository.PlayerRepository

/**
 * Keeps placed [MusicWidget]s in sync with playback. Observes the parts of the
 * player state the widget actually renders — the current song and play/pause flag —
 * and pushes a Glance update on each change, deliberately ignoring the 500ms
 * position ticks so the widget isn't rebuilt several times a second.
 */
object MusicWidgetUpdater {

    fun start(context: Context, player: PlayerRepository, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            player.playbackState
                .map { it.currentSong?.id to it.isPlaying }
                .distinctUntilChanged()
                .collect {
                    runCatching { MusicWidget().updateAll(appContext) }
                }
        }
    }
}
