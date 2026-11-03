package pl.dakil.music.presentation.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuLabel
import pl.dakil.music.ui.designsystem.AppMenuChoiceItem
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing

internal val Enum<*>.sortLabelRes: Int
    get() = when (this) {
        is AlbumSortOption -> labelRes
        is ArtistSortOption -> labelRes
        is GenreSortOption -> labelRes
        is PlaylistSortOption -> labelRes
        else -> 0
    }

/**
 * A pill showing the current sort ("Album name ↑") that opens a menu of the options. As before,
 * picking the option that's already active flips its direction; the arrow turns to match.
 */
@Composable
fun <T : Enum<T>> SortButton(
    options: List<T>,
    sort: SortState<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var expanded by remember { mutableStateOf(false) }
    val arrowTurn by animateFloatAsState(
        targetValue = if (sort.direction == SortDirection.ASC) 0f else 180f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sortArrow",
    )
    Box(modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.SmallContentPadding,
        ) {
            AppIcon(AppSymbols.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(sort.option.sortLabelRes), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(Spacing.xs))
            AppIcon(
                AppSymbols.ArrowUpward,
                contentDescription = stringResource(
                    if (sort.direction == SortDirection.ASC) R.string.sort_direction_asc else R.string.sort_direction_desc,
                ),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowTurn },
            )
        }
        AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppMenuGroup {
                AppMenuLabel(stringResource(R.string.sort_by))
                options.forEach { option ->
                    AppMenuChoiceItem(
                        text = stringResource(option.sortLabelRes),
                        selected = option == sort.option,
                        onClick = {
                            haptics.tick()
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
