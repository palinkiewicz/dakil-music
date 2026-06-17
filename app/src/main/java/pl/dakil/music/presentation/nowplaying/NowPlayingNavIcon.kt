package pl.dakil.music.presentation.nowplaying

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import pl.dakil.music.presentation.components.AlbumArt

/**
 * Bottom-bar icon for the Now Playing tab. With nothing loaded it is the plain
 * play-circle glyph; while a track is loaded it becomes the album-art thumbnail
 * encircled by a progress ring that animates toward the end of the song — giving an
 * at-a-glance sense of how much time is left.
 */
@Composable
fun NowPlayingNavIcon(
    albumArtUri: Uri?,
    hasSong: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (!hasSong) {
        Icon(imageVector = Icons.Rounded.PlayCircle, contentDescription = null, modifier = modifier)
        return
    }

    // Smoothly bridge the ~500ms position polling so the ring sweeps instead of jumping.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "navProgress",
    )

    // Match the standard 24dp nav icon footprint so the label stays aligned with the others.
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        AlbumArt(
            model = albumArtUri,
            shape = CircleShape,
            modifier = Modifier.size(16.dp),
        )
    }
}
