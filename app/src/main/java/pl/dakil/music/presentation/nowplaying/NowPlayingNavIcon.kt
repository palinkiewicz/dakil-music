package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/**
 * Icon for the Now Playing navigation item. With nothing loaded it is the
 * play-circle glyph; while a track is loaded it becomes the cover-art thumbnail
 * encircled by a wavy progress ring that ripples while music plays — giving an
 * at-a-glance sense of how much time is left.
 */
@Composable
fun NowPlayingNavIcon(
    artModel: Any?,
    hasSong: Boolean,
    progress: Float,
    isPlaying: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasSong) {
        AnimatedAppIcon(AppSymbols.PlayCircle, contentDescription = null, filled = selected, modifier = modifier)
        return
    }

    // Smoothly bridge the ~500ms position polling so the ring sweeps instead of jumping.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "navProgress",
    )

    val stroke = with(LocalDensity.current) { Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round) }

    // Match the standard 24dp nav icon footprint so the label stays aligned with the others.
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { if (isPlaying) 1f else 0f },
            wavelength = 6.dp,
            gapSize = 1.dp,
        )
        AlbumArt(
            model = artModel,
            shape = CircleShape,
            modifier = Modifier.size(15.dp),
        )
    }
}
