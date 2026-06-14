package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.clickableRow
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog

@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val showAddToPlaylist by viewModel.showAddToPlaylist.collectAsStateWithLifecycle()

    if (state.song == null) {
        EmptyNowPlaying(modifier)
        return
    }

    NowPlayingContent(
        state = state,
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSeek = viewModel::onSeek,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeat = viewModel::onCycleRepeat,
        onToggleFavorite = viewModel::onToggleFavorite,
        onAddToPlaylist = viewModel::openAddToPlaylist,
        onQueueItemClick = viewModel::onQueueItemClick,
        modifier = modifier,
    )

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            playlists = userPlaylists,
            onDismiss = viewModel::dismissAddToPlaylist,
            onSelect = viewModel::addCurrentToPlaylist,
            onCreateNew = viewModel::createPlaylistAndAddCurrent,
        )
    }
}

@Composable
private fun NowPlayingContent(
    state: NowPlayingUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "player") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Expressive, large rounded album art.
                AlbumArt(
                    uri = song.albumArtUri,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.artists.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                                ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(onClick = onAddToPlaylist) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.action_add_to_playlist),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val favScale by animateFloatAsState(
                        targetValue = if (state.isCurrentFavorite) 1.15f else 1f,
                        label = "favoriteScale",
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.graphicsLayer {
                        scaleX = favScale; scaleY = favScale
                    }) {
                        Icon(
                            imageVector = if (state.isCurrentFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (state.isCurrentFavorite) {
                                    R.string.cd_remove_from_favorites
                                } else {
                                    R.string.cd_add_to_favorites
                                },
                            ),
                            tint = if (state.isCurrentFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TimeBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = onSeek,
                )

                Spacer(Modifier.height(8.dp))

                TransportControls(
                    state = state,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                )
            }
        }

        if (state.queue.isNotEmpty()) {
            item(key = "queue_header") {
                Text(
                    text = stringResource(R.string.now_playing_queue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(state.queue, key = { index, _ -> index }) { index, queueSong ->
                QueueRow(
                    song = queueSong,
                    isCurrent = index == state.currentIndex,
                    onClick = { onQueueItemClick(index) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        leadingContent = {
            AlbumArt(
                uri = song.albumArtUri,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(44.dp),
            )
        },
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = song.artistsLabel.ifBlank { stringResource(R.string.unknown_artist) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (isCurrent) {
            { Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        modifier = Modifier.clickableRow(onClick),
    )
}

@Composable
private fun TimeBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    // While dragging we show the in-progress value; otherwise the live position.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue = dragValue ?: positionMs.toFloat().coerceIn(0f, duration)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..duration,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(sliderValue.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: NowPlayingUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                tint = if (state.shuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        FilledTonalIconButton(
            onClick = onPrevious,
            enabled = state.hasPrevious,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.cd_previous))
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
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.cd_next))
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = when (state.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = stringResource(R.string.cd_repeat),
                tint = if (state.repeatMode == RepeatMode.OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun EmptyNowPlaying(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.now_playing_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.now_playing_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
