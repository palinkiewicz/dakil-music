package pl.dakil.music.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import pl.dakil.music.R
import pl.dakil.music.data.coverart.EmbeddedArt
import pl.dakil.music.domain.model.Song

/**
 * The Coil model to render a song's cover art: its own embedded art when the song's
 * album is in individual-cover-art mode, otherwise the shared MediaStore album art.
 */
fun Song.coverArtModel(): Any? = if (individualCoverArt) EmbeddedArt(uri) else albumArtUri

/**
 * Album artwork with a graceful placeholder. Coil resolves the given [model] (a
 * MediaStore album-art Uri or an [EmbeddedArt] request); when none exists (or it
 * fails to load) a themed music-note tile is shown.
 */
@Composable
fun AlbumArt(
    model: Any?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }

    SubcomposeAsyncImage(
        model = model,
        contentDescription = stringResource(R.string.cd_album_art),
        contentScale = ContentScale.Crop,
        loading = { placeholder() },
        error = { placeholder() },
        modifier = modifier.clip(shape),
    )
}
