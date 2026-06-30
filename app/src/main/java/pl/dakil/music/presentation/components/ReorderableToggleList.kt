package pl.dakil.music.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One customizable navigation row: an item with a visibility switch and a drag handle. */
data class ToggleRow(
    val item: NavItem,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    /** When false the switch is shown but greyed out (e.g. forced-on or capped). */
    val switchEnabled: Boolean,
)

/**
 * A drag-to-reorder list of on/off rows, mirroring the Now Playing queue's reorder
 * behaviour (a long-press on the handle lifts the row; the move commits on drag stop).
 */
@Composable
fun ReorderableToggleList(
    rows: List<ToggleRow>,
    onToggle: (NavItem, Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Local working copy so the list animates smoothly during a drag; the real move is
    // committed once on drag stop (identical to the playlist/queue reorder).
    var localRows by remember { mutableStateOf(rows) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(rows) {
        if (!isDragging) localRows = rows
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in localRows.indices && to.index in localRows.indices) {
            localRows = localRows.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(localRows, key = { _, row -> row.item.name }) { index, row ->
            ReorderableItem(reorderState, key = row.item.name) { dragging ->
                val itemScope = this
                val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "rowElevation")
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = if (dragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.shadow(elevation),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.cd_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = with(itemScope) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        isDragging = true
                                        dragStartIndex = index
                                    },
                                    onDragStopped = {
                                        isDragging = false
                                        val to = localRows.indexOfFirst { it.item == row.item }
                                        if (dragStartIndex in localRows.indices &&
                                            to >= 0 && dragStartIndex != to
                                        ) {
                                            onMove(dragStartIndex, to)
                                        }
                                        dragStartIndex = -1
                                    },
                                )
                            }.size(24.dp),
                        )
                    },
                    headlineContent = { Text(row.label) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = row.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = row.enabled,
                                onCheckedChange = { onToggle(row.item, it) },
                                enabled = row.switchEnabled,
                            )
                        }
                    },
                )
            }
        }
    }
}
