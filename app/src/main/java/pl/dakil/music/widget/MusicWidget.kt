package pl.dakil.music.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.ColorFilter
import androidx.glance.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.MainActivity
import pl.dakil.music.MusicApplication
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer

/** Immutable view-model snapshot the widget renders; rebuilt on each Glance update. */
private data class WidgetSnapshot(
    val hasSong: Boolean,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap?,
)

/**
 * Home-screen widget showing the current track, its cover art and transport controls.
 *
 * It reads a one-shot snapshot of [pl.dakil.music.domain.repository.PlayerRepository]
 * (and a downscaled cover bitmap) when Glance asks for content; [MusicWidgetUpdater]
 * re-triggers that whenever the song or play/pause state changes. The widget is
 * freely resizable and adapts its layout to the available width.
 */
class MusicWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MusicApplication).container
        val snapshot = buildSnapshot(context, container)
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }

    private suspend fun buildSnapshot(context: Context, container: AppContainer): WidgetSnapshot {
        val state = container.playerRepository.playbackState.value
        val song = state.currentSong
        return WidgetSnapshot(
            hasSong = song != null,
            title = song?.title.orEmpty(),
            artist = song?.artistsLabel.orEmpty(),
            isPlaying = state.isPlaying,
            art = song?.let { WidgetArtLoader.load(context, it) },
        )
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val compact = size.width < 220.dp
    val showArtist = size.height >= 92.dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(snapshot)
        Spacer(GlanceModifier.width(12.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshot.title.ifBlank { textOrNothing(snapshot.hasSong) },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (showArtist && snapshot.artist.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = snapshot.artist,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            TransportRow(snapshot.isPlaying, compact)
        }
    }
}

@Composable
private fun CoverArt(snapshot: WidgetSnapshot) {
    val side = 64.dp
    Box(
        modifier = GlanceModifier
            .size(side)
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (snapshot.art != null) {
            Image(
                provider = ImageProvider(snapshot.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun TransportRow(isPlaying: Boolean, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            TransportButton(
                resId = R.drawable.ic_widget_skip_previous,
                onClick = actionRunCallback<PreviousAction>(),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        TransportButton(
            resId = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            onClick = actionRunCallback<PlayPauseAction>(),
            tint = GlanceTheme.colors.primary,
        )
        if (!compact) {
            Spacer(GlanceModifier.width(8.dp))
            TransportButton(
                resId = R.drawable.ic_widget_skip_next,
                onClick = actionRunCallback<NextAction>(),
            )
        }
    }
}

@Composable
private fun TransportButton(
    resId: Int,
    onClick: androidx.glance.action.Action,
    tint: ColorProvider = GlanceTheme.colors.onSurface,
) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

/** Empty-state label shown when nothing is playing. */
@Composable
private fun textOrNothing(hasSong: Boolean): String =
    if (hasSong) "" else androidx.glance.LocalContext.current.getString(R.string.widget_nothing_playing)

// --- Action callbacks ----------------------------------------------------------------

private suspend fun withController(context: Context, block: (AppContainer) -> Unit) {
    val container = (context.applicationContext as MusicApplication).container
    // The MediaController is main-thread only.
    withContext(Dispatchers.Main) { block(container) }
    MusicWidget().updateAll(context)
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) = withController(context) { it.playerRepository.togglePlayPause() }
}

class NextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) = withController(context) { it.playerRepository.next() }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) = withController(context) { it.playerRepository.previous() }
}
