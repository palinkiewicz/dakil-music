package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import pl.dakil.music.R
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
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
        onReselect = onReselect,
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onSeek = viewModel::onSeek,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeat = viewModel::onCycleRepeat,
        onToggleFavorite = viewModel::onToggleFavorite,
        onAddToPlaylist = viewModel::openAddToPlaylist,
        onQueueItemClick = viewModel::onQueueItemClick,
        onMoveQueueItem = viewModel::onMoveQueueItem,
        onRemoveQueueItem = viewModel::onRemoveQueueItem,
        onClearQueue = viewModel::onClearQueue,
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
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
) {
    val song = state.song ?: return

    // The list contains the player + queue header before the queue items.
    val headerCount = 2
    val lazyListState = rememberLazyListState()

    // Re-tapping the Now Playing tab scrolls back to the top.
    LaunchedEffect(onReselect) {
        onReselect.collect { lazyListState.animateScrollToItem(0) }
    }

    // Reordering is performed on a local working copy: while a row is held we only
    // move it visually (other rows shift but their real order is untouched) and we
    // commit a single move to the player when the hold ends. Committing each step
    // live made the held row teleport and could shuffle other rows by accident.
    var localQueue by remember { mutableStateOf(state.queue.toQueueEntries()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var currentKey by remember { mutableStateOf<String?>(null) }

    // Adopt the committed queue whenever it changes, except mid-drag. The player
    // keeps the same queue list instance across position ticks, so this only fires
    // on genuine queue/track changes — including the single move we commit ourselves.
    LaunchedEffect(state.queue, state.currentIndex) {
        if (!isDragging) {
            localQueue = state.queue.toQueueEntries()
            currentKey = localQueue.getOrNull(state.currentIndex)?.key
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromQueue = from.index - headerCount
        val toQueue = to.index - headerCount
        if (fromQueue in localQueue.indices && toQueue in localQueue.indices) {
            localQueue = localQueue.toMutableList().apply { add(toQueue, removeAt(fromQueue)) }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "player") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
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
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.now_playing_queue),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearQueue) {
                            Text(stringResource(R.string.action_clear_queue))
                        }
                    }
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.song_count, state.queue.size, state.queue.size,
                        ) + "  •  " + formatDuration(state.queue.sumOf { it.durationMs }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(localQueue, key = { _, entry -> entry.key }) { index, entry ->
                ReorderableItem(reorderableState, key = entry.key) { dragging ->
                    val itemScope = this
                    QueueRow(
                        song = entry.song,
                        isCurrent = entry.key == currentKey,
                        isDragging = dragging,
                        removeMode = state.queueRemoveMode,
                        onClick = { onQueueItemClick(index) },
                        onRemove = { onRemoveQueueItem(index) },
                        dragHandle = {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = stringResource(R.string.cd_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Long-press the handle to "pop out" the row and drag it.
                                // The reorder is committed once, on release.
                                modifier = with(itemScope) {
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            isDragging = true
                                            dragStartIndex = index
                                        },
                                        onDragStopped = {
                                            isDragging = false
                                            val to = localQueue.indexOfFirst { it.key == entry.key }
                                            if (dragStartIndex in localQueue.indices &&
                                                to >= 0 && dragStartIndex != to
                                            ) {
                                                onMoveQueueItem(dragStartIndex, to)
                                            }
                                            dragStartIndex = -1
                                        },
                                    )
                                }.size(24.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isDragging: Boolean,
    removeMode: QueueRemoveMode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    if (removeMode != QueueRemoveMode.SWIPE) {
        QueueListItem(song, isCurrent, isDragging, removeMode, onClick, onRemove, dragHandle)
        return
    }

    // Either direction removes; the row is only dropped once the user *releases* past a
    // third of the way across the screen. We key on settledValue (not currentValue, which
    // flips mid-drag) so swiping to the edge and back without releasing leaves it in place.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance / 3f },
    )
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeRemoveBackground(dismissState.dismissDirection) },
    ) {
        QueueListItem(song, isCurrent, isDragging, removeMode, onClick, onRemove, dragHandle)
    }
}

/** A queue row paired with a stable per-instance key that survives reordering. */
private data class QueueEntry(val key: String, val song: Song)

/** Builds stable keys for the queue, disambiguating duplicate songs by occurrence. */
private fun List<Song>.toQueueEntries(): List<QueueEntry> {
    val seen = HashMap<Long, Int>()
    return map { song ->
        val occurrence = seen.merge(song.id, 1, Int::plus)
        QueueEntry("${song.id}#$occurrence", song)
    }
}

/** Red-tinted background revealed while swiping a queue row away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRemoveBackground(direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.action_remove_from_queue),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueListItem(
    song: Song,
    isCurrent: Boolean,
    isDragging: Boolean,
    removeMode: QueueRemoveMode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "queueElevation")
    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val clickModifier = if (removeMode == QueueRemoveMode.MENU) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = Modifier
            .shadow(elevation)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = containerColor),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (removeMode == QueueRemoveMode.BUTTON) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_remove_from_queue),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                    AlbumArt(
                        uri = song.albumArtUri,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(44.dp),
                    )
                }
            },
            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    text = song.artistsLabel.ifBlank { stringResource(R.string.unknown_artist) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isCurrent) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelMedium)
                    dragHandle()
                    if (removeMode == QueueRemoveMode.MENU) {
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_remove_from_queue)) },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            },
            modifier = clickModifier,
        )
    }
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
        FilledTonalIconButton(
            onClick = onToggleShuffle,
            colors = if (state.shuffleEnabled) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
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

        FilledTonalIconButton(
            onClick = onCycleRepeat,
            colors = if (state.repeatMode != RepeatMode.OFF) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Icon(
                imageVector = when (state.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = stringResource(R.string.cd_repeat),
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
