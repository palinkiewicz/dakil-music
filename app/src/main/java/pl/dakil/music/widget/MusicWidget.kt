package pl.dakil.music.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.ColorFilter
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.MainActivity
import pl.dakil.music.MusicApplication
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.PlaybackState

/**
 * Home-screen widget showing the current track, its cover art and transport controls.
 *
 * Playback state, favorites and progress are observed live from the repositories inside
 * the composition, so they reflect immediately. Cover art (suspending IO Glance can't do
 * mid-composition) is decoded by [MusicWidgetUpdater] into [WidgetArtHolder] on each song
 * change and read synchronously here. [SizeMode.Exact] gives the widget's real size, so
 * the cover and layout scale to fill it and adapt across sizes.
 */
class MusicWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MusicApplication).container
        // Make sure art for the current song is ready before the first frame.
        val current = container.playerRepository.playbackState.value.currentSong
        if (current != null && WidgetArtHolder.artFor(current.id) == null) {
            WidgetArtHolder.set(current.id, WidgetArt.load(context, container, current))
        }
        provideContent {
            GlanceTheme {
                val state by container.playerRepository.playbackState.collectAsState()
                val favorites by container.observeFavorites().collectAsState(emptySet())
                val song = state.currentSong
                WidgetContent(
                    state = state,
                    art = WidgetArtHolder.artFor(song?.id),
                    isFavorite = song != null && song.id in favorites,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(state: PlaybackState, art: Bitmap?, isFavorite: Boolean) {
    val size = LocalSize.current
    val pad = 10.dp
    val availH = (size.height - pad * 2).coerceAtLeast(0.dp)
    val availW = (size.width - pad * 2).coerceAtLeast(0.dp)

    val base = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(24.dp)
        .padding(pad)
        .clickable(actionStartActivity<MainActivity>())

    when {
        // Tall / squarish: big cover filling the free space, plus every control.
        size.height >= 150.dp && size.width < size.height * 1.5f ->
            TallLayout(state, art, isFavorite, base)
        // Wide: cover fills the height, with title, favorite, progress and full controls.
        size.width >= 230.dp ->
            WideLayout(state, art, isFavorite, availH, base)
        // Medium: cover, title and a single play/pause.
        size.width >= 150.dp -> {
            val cover = availH.coerceAtLeast(40.dp)
            Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
                CoverArt(art, GlanceModifier.size(cover), cover * 0.45f)
                Spacer(GlanceModifier.width(10.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    TitleBlock(state, centered = false, compact = true)
                }
                Spacer(GlanceModifier.width(8.dp))
                PlayPauseButton(state)
            }
        }
        // Small: play/pause first (sized to fit), then the cover fills the leftover width.
        else -> {
            // Inset from the reported height so the button stays inside the card even
            // with the launcher's own widget margins.
            val button = minOf(availH - 8.dp, availW).coerceIn(24.dp, 44.dp)
            val cover = minOf(availH, availW - button - 8.dp).coerceAtLeast(0.dp)
            Row(
                modifier = base,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = if (cover > 0.dp) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                PlayPauseButton(state, button)
                if (cover > 0.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    CoverArt(art, GlanceModifier.size(cover), cover * 0.45f)
                }
            }
        }
    }
}

@Composable
private fun TallLayout(state: PlaybackState, art: Bitmap?, isFavorite: Boolean, base: GlanceModifier) {
    Column(modifier = base, horizontalAlignment = Alignment.CenterHorizontally) {
        // The cover takes all the height left over by the fixed controls below.
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CoverArt(art, GlanceModifier.fillMaxSize(), 56.dp)
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = GlanceModifier.defaultWeight()) {
                TitleBlock(state, centered = false, compact = false)
            }
            FavoriteButton(isFavorite)
        }
        Spacer(GlanceModifier.height(6.dp))
        ProgressBar(state)
        Spacer(GlanceModifier.height(6.dp))
        TransportRow(state, full = true)
    }
}

@Composable
private fun WideLayout(
    state: PlaybackState,
    art: Bitmap?,
    isFavorite: Boolean,
    availH: Dp,
    base: GlanceModifier,
) {
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        CoverArt(art, GlanceModifier.size(availH.coerceAtLeast(48.dp)), 56.dp)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    TitleBlock(state, centered = false, compact = true)
                }
                FavoriteButton(isFavorite)
            }
            Spacer(GlanceModifier.height(6.dp))
            ProgressBar(state)
            Spacer(GlanceModifier.height(6.dp))
            TransportRow(state, full = true)
        }
    }
}

@Composable
private fun TitleBlock(state: PlaybackState, centered: Boolean, compact: Boolean) {
    val song = state.currentSong
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = song?.title ?: LocalContext.current.getString(R.string.widget_nothing_playing),
            maxLines = if (compact) 1 else 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
        )
        val artist = song?.artistsLabel.orEmpty()
        if (artist.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun ProgressBar(state: PlaybackState) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = progress,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
}

@Composable
private fun CoverArt(art: Bitmap?, modifier: GlanceModifier, iconSize: Dp) {
    Box(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                provider = ImageProvider(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun TransportRow(state: PlaybackState, full: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (full) {
            TransportButton(R.drawable.ic_widget_skip_previous, actionRunCallback<PreviousAction>())
            Spacer(GlanceModifier.width(12.dp))
        }
        PlayPauseButton(state)
        if (full) {
            Spacer(GlanceModifier.width(12.dp))
            TransportButton(R.drawable.ic_widget_skip_next, actionRunCallback<NextAction>())
        }
    }
}

@Composable
private fun PlayPauseButton(state: PlaybackState, buttonSize: Dp = 44.dp) {
    TransportButton(
        resId = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        onClick = actionRunCallback<PlayPauseAction>(),
        tint = GlanceTheme.colors.primary,
        buttonSize = buttonSize,
    )
}

@Composable
private fun FavoriteButton(isFavorite: Boolean) {
    TransportButton(
        resId = if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
        onClick = actionRunCallback<ToggleFavoriteAction>(),
        tint = if (isFavorite) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun TransportButton(
    resId: Int,
    onClick: Action,
    tint: ColorProvider = GlanceTheme.colors.onSurface,
    buttonSize: Dp = 44.dp,
) {
    Box(
        modifier = GlanceModifier
            .size(buttonSize)
            .cornerRadius(buttonSize / 2f)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(buttonSize * 0.62f),
        )
    }
}

// --- Action callbacks ----------------------------------------------------------------

private fun appContainer(context: Context): AppContainer =
    (context.applicationContext as MusicApplication).container

private suspend fun withController(context: Context, block: (AppContainer) -> Unit) {
    val container = appContainer(context)
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

class ToggleFavoriteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val container = appContainer(context)
        val songId = container.playerRepository.playbackState.value.currentSong?.id ?: return
        container.toggleFavorite(songId)
        MusicWidget().updateAll(context)
    }
}
