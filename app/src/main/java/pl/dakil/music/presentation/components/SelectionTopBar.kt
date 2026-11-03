package pl.dakil.music.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pl.dakil.music.R
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.RollingNumber
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing

/**
 * The chrome shown while songs are selected for a bulk action, shared by every list that supports
 * multi-select (song lists, library search, history): a top bar with the rolling count, close and
 * select-all, and a floating toolbar with the actions — favourite and queue up front, the rest in
 * an overflow menu. Both slide in and out with the selection. [showRemoveFromPlaylist] adds
 * "remove from playlist" (user-playlist sources only); null actions are left out (history can't
 * change cover art or show file info). Place it over the screen's content.
 */
@Composable
fun SelectionChrome(
    visible: Boolean,
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
    onChangeCoverArt: (() -> Unit)?,
    onRemoveFromPlaylist: () -> Unit,
    onShare: (() -> Unit)?,
    onShowInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    onTopBarHeightChanged: (Int) -> Unit = {},
) {
    val haptics = rememberHaptics()
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopAppBar(
                windowInsets = windowInsets,
                title = {
                    RollingNumber(
                        pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.Close, stringResource(R.string.action_close_selection))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onSelectAll()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        AppIcon(AppSymbols.SelectAll, stringResource(R.string.action_select_all))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.onSizeChanged { onTopBarHeightChanged(it.height) },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f) + fadeIn(),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.l),
        ) {
            var menuExpanded by remember { mutableStateOf(false) }
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                IconButton(
                    onClick = {
                        haptics.toggle(!allSelectedFavorite)
                        onToggleFavorites()
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    AnimatedAppIcon(
                        AppSymbols.Favorite,
                        contentDescription = stringResource(
                            if (allSelectedFavorite) R.string.action_remove_from_favorites else R.string.action_add_to_favorites,
                        ),
                        filled = allSelectedFavorite,
                    )
                }
                ToolbarAction(AppSymbols.QueueMusic, stringResource(R.string.action_add_to_queue)) {
                    haptics.confirm()
                    onAddToQueue()
                }
                ToolbarAction(AppSymbols.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist)
                val multi = selectedCount > 1
                if (onShare != null) {
                    ToolbarAction(
                        AppSymbols.Share,
                        stringResource(if (multi) R.string.action_share_files else R.string.action_share_file),
                        onShare,
                    )
                }
                Box {
                    ToolbarAction(AppSymbols.MoreVert, stringResource(R.string.action_more)) { menuExpanded = true }
                    AppMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        AppMenuGroup(index = 0, count = 2) {
                            if (onChangeCoverArt != null) {
                                AppMenuItem(
                                    // "Add" when the lone selected song has no art, else "Change".
                                    text = stringResource(
                                        if (!singleSelectionHasArt) R.string.action_add_cover_art else R.string.action_change_cover_art,
                                    ),
                                    symbol = AppSymbols.Image,
                                    onClick = { menuExpanded = false; onChangeCoverArt() },
                                )
                            }
                            AppMenuItem(
                                text = stringResource(R.string.action_edit_tags),
                                symbol = AppSymbols.Label,
                                onClick = { menuExpanded = false; onEditTags() },
                            )
                            AppMenuItem(
                                text = stringResource(R.string.action_decompose_title),
                                symbol = AppSymbols.CallSplit,
                                onClick = { menuExpanded = false; onDecompose() },
                            )
                            if (onShowInfo != null) {
                                AppMenuItem(
                                    text = stringResource(if (multi) R.string.action_files_info else R.string.action_file_info),
                                    symbol = AppSymbols.Info,
                                    onClick = { menuExpanded = false; onShowInfo() },
                                )
                            }
                        }
                        AppMenuGroup(index = 1, count = 2) {
                            if (showRemoveFromPlaylist) {
                                AppMenuItem(
                                    text = stringResource(R.string.action_remove_from_playlist),
                                    symbol = AppSymbols.PlaylistRemove,
                                    destructive = true,
                                    onClick = { menuExpanded = false; onRemoveFromPlaylist() },
                                )
                            }
                            AppMenuItem(
                                text = stringResource(R.string.action_select_all),
                                symbol = AppSymbols.SelectAll,
                                onClick = { menuExpanded = false; onSelectAll() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(symbol: AppSymbol, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        AppIcon(symbol, contentDescription = description)
    }
}
