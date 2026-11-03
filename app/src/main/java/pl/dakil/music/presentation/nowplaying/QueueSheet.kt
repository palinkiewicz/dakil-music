package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.dakil.music.R
import pl.dakil.music.domain.model.QueueRemoveMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.NowPlayingBars
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The play queue as a modal sheet: drag a row's handle to reorder, remove it the way the user chose
 * in settings ([QueueRemoveMode]), tap a row to jump to it. Opens scrolled to the current song.
 */
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    removeMode: QueueRemoveMode,
    onDismiss: () -> Unit,
    onItemClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            QueueHeader(
                queue = queue,
                onClear = {
                    haptics.confirm()
                    onClear()
                    onDismiss()
                },
            )
            QueueList(
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                removeMode = removeMode,
                onItemClick = onItemClick,
                onMove = onMove,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun QueueHeader(queue: List<Song>, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.xl, end = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.now_playing_queue),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                pluralStringResource(R.plurals.song_count, queue.size, queue.size) +
                    "  ·  " + formatDuration(queue.sumOf { it.durationMs }),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (queue.isNotEmpty()) {
            FilledTonalButton(onClick = onClear) {
                AppIcon(AppSymbols.PlaylistRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.action_clear_queue))
            }
        }
    }
}

@Composable
private fun QueueList(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    removeMode: QueueRemoveMode,
    onItemClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))

    // Reordering is performed on a local working copy: while a row is held we only
    // move it visually (other rows shift but their real order is untouched) and we
    // commit a single move to the player when the hold ends. Committing each step
    // live made the held row teleport and could shuffle other rows by accident.
    var localQueue by remember { mutableStateOf(queue.toQueueEntries()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var currentKey by remember { mutableStateOf<String?>(null) }

    // Adopt the committed queue whenever it changes, except mid-drag. The player
    // keeps the same queue list instance across position ticks, so this only fires
    // on genuine queue/track changes — including the single move we commit ourselves.
    LaunchedEffect(queue, currentIndex) {
        if (!isDragging) {
            localQueue = queue.toQueueEntries()
            currentKey = localQueue.getOrNull(currentIndex)?.key
        }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in localQueue.indices && to.index in localQueue.indices) {
            localQueue = localQueue.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptics.frequentTick()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        itemsIndexed(localQueue, key = { _, entry -> entry.key }) { index, entry ->
            ReorderableItem(reorderableState, key = entry.key) { dragging ->
                val itemScope = this
                QueueRow(
                    song = entry.song,
                    isCurrent = entry.key == currentKey,
                    isPlaying = isPlaying,
                    isDragging = dragging,
                    removeMode = removeMode,
                    onClick = { onItemClick(index) },
                    onRemove = {
                        haptics.confirm()
                        onRemove(index)
                    },
                    dragHandle = {
                        AppIcon(
                            AppSymbols.DragIndicator,
                            contentDescription = stringResource(R.string.cd_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = with(itemScope) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        haptics.longPress()
                                        isDragging = true
                                        dragStartIndex = index
                                    },
                                    onDragStopped = {
                                        isDragging = false
                                        val to = localQueue.indexOfFirst { it.key == entry.key }
                                        if (dragStartIndex in localQueue.indices &&
                                            to >= 0 && dragStartIndex != to
                                        ) {
                                            haptics.confirm()
                                            onMove(dragStartIndex, to)
                                        }
                                        dragStartIndex = -1
                                    },
                                )
                            }.size(24.dp),
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    removeMode: QueueRemoveMode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (removeMode != QueueRemoveMode.SWIPE) {
        QueueTile(song, isCurrent, isPlaying, isDragging, removeMode, onClick, onRemove, dragHandle, modifier)
        return
    }

    val haptics = rememberHaptics()
    // Either direction removes; the row is only dropped once the user *releases* past a
    // third of the way across the screen. We key on settledValue (not currentValue, which
    // flips mid-drag) so swiping to the edge and back without releasing leaves it in place.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance / 3f },
    )
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue != SwipeToDismissBoxValue.Settled) onRemove()
    }
    // A tick the moment the swipe crosses the point where letting go would remove the row.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue != SwipeToDismissBoxValue.Settled }
            .distinctUntilChanged()
            .collect { armed -> if (armed) haptics.threshold() }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(MaterialTheme.shapes.large),
        backgroundContent = { SwipeRemoveBackground(dismissState.dismissDirection) },
    ) {
        QueueTile(song, isCurrent, isPlaying, isDragging, removeMode, onClick, onRemove, dragHandle)
    }
}

/** The error-tinted strip revealed while swiping a queue row away. */
@Composable
private fun SwipeRemoveBackground(direction: SwipeToDismissBoxValue) {
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.xl),
        contentAlignment = alignment,
    ) {
        AppIcon(
            AppSymbols.Delete,
            contentDescription = stringResource(R.string.action_remove_from_queue),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun QueueTile(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    removeMode: QueueRemoveMode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "queueElevation")
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "queueLift",
    )
    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val clickModifier = if (removeMode == QueueRemoveMode.MENU) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .shadow(elevation, MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = clickModifier.padding(start = Spacing.s, end = Spacing.m, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (removeMode == QueueRemoveMode.BUTTON) {
                IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(
                        AppSymbols.Close,
                        contentDescription = stringResource(R.string.action_remove_from_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) {
                AlbumArt(
                    model = song.coverArtModel(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp),
                )
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingBars(isPlaying = isPlaying, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 1f))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = if (isCurrent) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artistsLabel.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                dragHandle()
                if (removeMode == QueueRemoveMode.MENU) {
                    AppMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        AppMenuGroup {
                            AppMenuItem(
                                text = stringResource(R.string.action_remove_from_queue),
                                symbol = AppSymbols.PlaylistRemove,
                                destructive = true,
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
        }
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
