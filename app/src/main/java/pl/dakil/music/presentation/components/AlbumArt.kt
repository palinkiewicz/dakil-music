package pl.dakil.music.presentation.components

import android.net.Uri
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

/**
 * Album artwork with a graceful placeholder. Coil resolves the MediaStore album-art
 * [Uri]; when none exists (or it fails to load) a themed music-note tile is shown.
 */
@Composable
fun AlbumArt(
    uri: Uri?,
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
        model = uri,
        contentDescription = stringResource(R.string.cd_album_art),
        contentScale = ContentScale.Crop,
        loading = { placeholder() },
        error = { placeholder() },
        modifier = modifier.clip(shape),
    )
}
