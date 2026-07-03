package pl.dakil.music.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pl.dakil.music.R

/**
 * Top bar shown while songs are selected for a bulk action. Shared by every list that
 * supports multi-select (song lists and the library search). The favorites toggle and
 * "add to queue" are surfaced directly; the rest live behind an overflow menu.
 * [showRemoveFromPlaylist] adds a "remove from playlist" entry (user-playlist sources
 * only); other callers pass `false` and a no-op [onRemoveFromPlaylist].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    singleSelectionHasArt: Boolean,
    showRemoveFromPlaylist: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleFavorites: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onDecompose: () -> Unit,
    onChangeCoverArt: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        windowInsets = windowInsets,
        title = {
            Text(pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount))
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close_selection))
            }
        },
        actions = {
            IconButton(onClick = onToggleFavorites) {
                Icon(
                    imageVector = if (allSelectedFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (allSelectedFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites,
                    ),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Rounded.QueueMusic, stringResource(R.string.action_add_to_queue))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                        onClick = { menuExpanded = false; onAddToPlaylist() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    // "Add" when the lone selected song has no art, else "Change".
                                    if (!singleSelectionHasArt) R.string.action_add_cover_art else R.string.action_change_cover_art,
                                ),
                            )
                        },
                        onClick = { menuExpanded = false; onChangeCoverArt() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit_tags)) },
                        onClick = { menuExpanded = false; onEditTags() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_decompose_title)) },
                        onClick = { menuExpanded = false; onDecompose() },
                    )
                    if (showRemoveFromPlaylist) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_from_playlist)) },
                            onClick = { menuExpanded = false; onRemoveFromPlaylist() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        onClick = { menuExpanded = false; onSelectAll() },
                    )
                    val multi = selectedCount > 1
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (multi) R.string.action_share_files else R.string.action_share_file,
                                ),
                            )
                        },
                        onClick = { menuExpanded = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (multi) R.string.action_files_info else R.string.action_file_info,
                                ),
                            )
                        },
                        onClick = { menuExpanded = false; onShowInfo() },
                    )
                }
            }
        },
        modifier = modifier,
    )
}
