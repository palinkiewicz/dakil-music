package pl.dakil.music.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class ToggleRow(
    val item: NavItem,
    val label: String,
    val icon: AppSymbol,
    val enabled: Boolean,
    /** When false the switch is shown but greyed out (e.g. forced-on or capped). */
    val switchEnabled: Boolean,
)

/**
 * A drag-to-reorder list of on/off tiles, mirroring the player queue's reorder
 * behaviour (a long-press on the handle lifts the row; the move commits on drag stop).
 */
@Composable
fun ReorderableToggleList(
    rows: List<ToggleRow>,
    onToggle: (NavItem, Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()

    // Local working copy so the list animates smoothly during a drag; the real move is
    // committed once on drag stop (identical to the playlist/queue reorder).
    var localRows by remember { mutableStateOf(rows) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(rows) {
        if (!isDragging) localRows = rows
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in localRows.indices && to.index in localRows.indices) {
            localRows = localRows.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptics.frequentTick()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(localRows, key = { _, row -> row.item.name }) { index, row ->
            ReorderableItem(reorderState, key = row.item.name) { dragging ->
                val itemScope = this
                val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "rowElevation")
                val container by animateColorAsState(
                    if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                    label = "rowColor",
                )
                val shape = if (dragging) MaterialTheme.shapes.large else segmentedItemShape(index, localRows.size)
                Surface(
                    color = container,
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation, shape),
                ) {
                    Row(
                        modifier = Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        ShapedIcon(
                            row.icon,
                            modifier = Modifier.alpha(if (row.enabled) 1f else 0.5f),
                            filled = row.enabled,
                        )
                        Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = {
                                haptics.toggle(it)
                                onToggle(row.item, it)
                            },
                            enabled = row.switchEnabled,
                            thumbContent = if (row.enabled) {
                                { AppIcon(AppSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
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
                                        val to = localRows.indexOfFirst { it.item == row.item }
                                        if (dragStartIndex in localRows.indices &&
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
                    }
                }
            }
        }
    }
}
