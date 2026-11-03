package pl.dakil.music.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.music.R
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.albumAuthorModeNameRes
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing

/** Every per-album override, as segmented tiles that can be deleted to fall back to the globals. */
@Composable
fun AlbumRulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumRulesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    CollapsingScaffold(
        title = stringResource(R.string.settings_album_rules),
        navigationIcon = { BackButton(onBack) },
        modifier = modifier,
    ) { padding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    symbol = AppSymbols.Rule,
                    title = stringResource(R.string.album_rules_empty),
                    polygon = MaterialShapes.Clover4Leaf,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
            ) {
                itemsIndexed(rules, key = { _, item -> item.rule.albumKey }) { index, item ->
                    // A hidden album has no entry for a cover-art or author override to
                    // apply to, so those are never stored alongside it and listing them
                    // here would only be noise.
                    val parts = if (item.rule.treatAsNoAlbum) {
                        listOf(stringResource(R.string.album_rules_no_album_label))
                    } else {
                        buildList {
                            item.rule.coverArtMode?.let { add(stringResource(R.string.album_rules_cover_art_label)) }
                            item.rule.authorMode?.let { add(stringResource(albumAuthorModeNameRes(it))) }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = segmentedItemShape(index, rules.size),
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            ShapedIcon(if (item.rule.treatAsNoAlbum) AppSymbols.FolderOff else AppSymbols.Album)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title.ifBlank { stringResource(R.string.unknown_album) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    parts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.confirm()
                                    viewModel.delete(item.rule.albumKey)
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                AppIcon(AppSymbols.Delete, contentDescription = stringResource(R.string.album_rules_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}
