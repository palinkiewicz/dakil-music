package pl.dakil.music.presentation.quickplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.TimeBar
import pl.dakil.music.presentation.components.coverArtModel

/**
 * Stripped-down player for a single externally opened audio file: artwork, track
 * info, seekbar and basic transport. Deliberately no queue, lyrics, favorites or
 * bottom navigation — this screen is an isolated, transient viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlayerScreen(
    state: QuickPlayerUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenApp) {
                        Text(stringResource(R.string.quick_player_open_app))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val song = state.song

            AlbumArt(
                model = song?.coverArtModel(),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = song?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.quick_player_unknown_title),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                    ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(16.dp))

            TimeBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onSeekBy(-SEEK_STEP_MS) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Replay10, stringResource(R.string.cd_seek_back))
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                ) {
                    AnimatedContent(targetState = state.isPlaying, label = "playPause") { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) R.string.cd_pause else R.string.cd_play,
                            ),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { onSeekBy(SEEK_STEP_MS) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Forward10, stringResource(R.string.cd_seek_forward))
                }
            }
        }
    }
}

private const val SEEK_STEP_MS = 10_000L
