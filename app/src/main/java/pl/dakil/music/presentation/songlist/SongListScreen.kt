package pl.dakil.music.presentation.songlist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pl.dakil.music.R
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.NO_ALBUM_ID
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.ArtistAvatar
import pl.dakil.music.presentation.components.FileInfoDialog
import pl.dakil.music.presentation.components.SelectionChrome
import pl.dakil.music.presentation.components.SongLeading
import pl.dakil.music.presentation.components.SongPickerDialog
import pl.dakil.music.presentation.components.SongRow
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.shareSongs
import pl.dakil.music.presentation.components.toSongEntries
import pl.dakil.music.presentation.library.albumArtKey
import pl.dakil.music.presentation.library.systemPlaylistNameRes
import pl.dakil.music.presentation.navigation.SongListSource
import pl.dakil.music.presentation.nowplaying.ArtTheme
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.playlist.PlaylistNameDialog
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.sharedElement
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SongListScreen(
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    // Hidden when the list is a bottom-bar destination (a tab has nothing to go back to).
    showBack: Boolean = true,
    viewModel: SongListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val currentAlbum by viewModel.currentAlbum.collectAsStateWithLifecycle()
    val authoredAlbums by viewModel.authoredAlbums.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val coverArtVersion by viewModel.coverArtVersion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberHaptics()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted()
        }
    }

    // Native png/jpeg picker for cover art. pickForAlbum routes the result.
    var pickForAlbum by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onCoverArtPicked(context.contentResolver, uri, pickForAlbum)
    }
    fun launchImagePicker(forAlbum: Boolean) {
        pickForAlbum = forAlbum
        imagePicker.launch(arrayOf("image/png", "image/jpeg"))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SongListEvent.Message ->
                    snackbarHostState.showSnackbar(resources.getString(event.res))

                is SongListEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())

                is SongListEvent.NavigateBack -> onBack()
            }
        }
    }

    // Back leaves the selection mode first instead of leaving the screen.
    BackHandler(enabled = state.inSelectionMode) {
        viewModel.clearSelection()
    }

    val source = viewModel.source
    val title = headerTitle(source, state.songs, userPlaylists)
    val isAlbum = source is SongListSource.AlbumSource
    val isNoAlbumSource = source is SongListSource.AlbumSource && source.albumId == NO_ALBUM_ID
    val isPerformer = source is SongListSource.PerformerSource
    val isUserPlaylist = source is SongListSource.UserPlaylistSource
    val isFavorites = source is SongListSource.PlaylistSource && source.playlist == SystemPlaylist.FAVORITES
    val canAddToPlaylist = isUserPlaylist || isFavorites
    val artShape = RoundedCornerShape(settings.albumCornerRoundnessDp.dp)
    val heroArt = state.songs.firstOrNull()?.coverArtModel(coverArtVersion)

    val density = LocalDensity.current
    // Measured height of the contextual selection bar. In embedded mode the list is padded
    // below it so the bar never covers a song (there's no hero to overlay).
    var selectionBarHeightPx by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    // Once the hero has scrolled away, pin the title in the top bar and offer the play FAB.
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 }
    }

    // Local working copy for playlist drag-reorder (mirrors the player's queue).
    var localEntries by remember { mutableStateOf(state.songs.toSongEntries()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.songs) {
        if (!isDragging) localEntries = state.songs.toSongEntries()
    }
    // Exactly one item (the hero, or the embedded actions row) precedes playlist songs.
    val headerCount = 1
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - headerCount
        val t = to.index - headerCount
        if (f in localEntries.indices && t in localEntries.indices) {
            localEntries = localEntries.toMutableList().apply { add(t, removeAt(f)) }
            haptics.frequentTick()
        }
    }
    val reorderable = (isUserPlaylist || isFavorites) && !state.inSelectionMode

    // Album pages take their colors from the cover, like the player.
    ArtTheme(artModel = if (isAlbum && !isNoAlbumSource && !embedded) heroArt else null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (state.songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(AppSymbols.MusicNote, stringResource(R.string.library_empty_songs))
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        // Embedded selection bar sits in the layout's top edge; pad below it.
                        top = if (embedded && state.inSelectionMode) {
                            with(density) { selectionBarHeightPx.toDp() }
                        } else {
                            0.dp
                        },
                        start = Spacing.s,
                        end = Spacing.s,
                        bottom = 120.dp, // clear the FAB / selection toolbar
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!embedded) {
                        item(key = "header") {
                            SongListHero(
                                title = title,
                                visual = when {
                                    isPerformer -> HeroVisual.Artist((source as SongListSource.PerformerSource).name)
                                    isFavorites -> HeroVisual.Symbol(AppSymbols.Favorite, MaterialShapes.Heart)
                                    else -> HeroVisual.Art(
                                        model = heroArt,
                                        sharedKey = (source as? SongListSource.AlbumSource)?.albumId?.let(::albumArtKey),
                                    )
                                },
                                shape = artShape,
                                author = if (isAlbum) currentAlbum?.artist?.takeIf { it.isNotBlank() } else null,
                                year = if (isAlbum) currentAlbum?.year ?: 0 else 0,
                                songCount = state.songs.size,
                                totalDurationMs = state.songs.sumOf { it.durationMs },
                                onPlay = viewModel::playAll,
                                onShuffle = viewModel::shufflePlay,
                            )
                        }
                    } else {
                        item(key = "embedded_actions") {
                            PlayShuffleRow(
                                onPlay = viewModel::playAll,
                                onShuffle = viewModel::shufflePlay,
                                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
                            )
                        }
                    }

                    if (isPerformer && authoredAlbums.isNotEmpty()) {
                        item(key = "authored_albums") {
                            AuthoredAlbumsSection(
                                albums = authoredAlbums,
                                shape = artShape,
                                onAlbumClick = onAlbumClick,
                                coverArtVersion = coverArtVersion,
                            )
                        }
                        item(key = "songs_subheader") {
                            SectionLabel(stringResource(R.string.songs_section))
                        }
                    }

                    if (reorderable) {
                        itemsIndexed(localEntries, key = { _, entry -> entry.key }) { index, entry ->
                            ReorderableItem(reorderState, key = entry.key) { dragging ->
                                val itemScope = this
                                // The drag handle lives inside the row (as trailing content), so
                                // the whole item is one clickable surface and the tap ripple spans
                                // the handle too — exactly like the player's queue. The handle
                                // grabs on long-press; the guard below stops that same long-press
                                // from also triggering the row's select.
                                var handlePressed by remember { mutableStateOf(false) }
                                SongRow(
                                    song = entry.song,
                                    leading = SongLeading.Art(entry.song.coverArtModel(coverArtVersion)),
                                    onClick = { viewModel.onSongClick(index) },
                                    onLongClick = {
                                        if (!handlePressed) viewModel.onSongLongClick(entry.song.id)
                                    },
                                    favorite = state.isFavorite(entry.song.id),
                                    current = state.isCurrent(entry.song.id),
                                    dragging = dragging,
                                    trailing = {
                                        AppIcon(
                                            AppSymbols.DragIndicator,
                                            contentDescription = stringResource(R.string.cd_reorder),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = with(itemScope) {
                                                Modifier
                                                    // Flag a press on the handle (without consuming, so
                                                    // the drag still works) so the row skips selection.
                                                    .pointerInput(Unit) {
                                                        awaitEachGesture {
                                                            awaitFirstDown(requireUnconsumed = false)
                                                            handlePressed = true
                                                            while (awaitPointerEvent().changes.any { it.pressed }) {
                                                                // wait until the finger lifts
                                                            }
                                                            handlePressed = false
                                                        }
                                                    }
                                                    .longPressDraggableHandle(
                                                        onDragStarted = {
                                                            haptics.longPress()
                                                            isDragging = true
                                                            dragStartIndex = index
                                                        },
                                                        onDragStopped = {
                                                            isDragging = false
                                                            val to = localEntries.indexOfFirst { it.key == entry.key }
                                                            if (dragStartIndex in localEntries.indices &&
                                                                to >= 0 && dragStartIndex != to
                                                            ) {
                                                                haptics.confirm()
                                                                viewModel.movePlaylistSong(dragStartIndex, to)
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
                    } else {
                        itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song,
                                leading = if (isAlbum && !isNoAlbumSource) {
                                    SongLeading.TrackNumber(song.trackNumber)
                                } else {
                                    SongLeading.Art(song.coverArtModel(coverArtVersion))
                                },
                                onClick = { viewModel.onSongClick(index) },
                                onLongClick = { viewModel.onSongLongClick(song.id) },
                                selectionMode = state.inSelectionMode,
                                selected = state.isSelected(song.id),
                                favorite = state.isFavorite(song.id),
                                current = state.isCurrent(song.id),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            if (!embedded) {
                AnimatedVisibility(
                    visible = !state.inSelectionMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    SongListTopBar(
                        title = title,
                        collapsed = collapsed,
                        showBack = showBack,
                        onBack = onBack,
                        onAddToQueue = {
                            haptics.confirm()
                            viewModel.addAllToQueue()
                        },
                        onAddToPlaylist = if (canAddToPlaylist) viewModel::startAddSongToPlaylist else null,
                        // "No album" is synthetic: it has no shared art and no album key of its
                        // own, and the songs under it may come from several real albums, so
                        // album-wide editing there would rewrite whichever album happened to
                        // sort first.
                        onEdit = when {
                            isAlbum -> if (isNoAlbumSource) null else viewModel::openAlbumEdit
                            isPerformer -> viewModel::openArtistEdit
                            isUserPlaylist -> viewModel::openPlaylistEdit
                            else -> null
                        },
                        onChangeCoverArt = if (isAlbum && !isNoAlbumSource) {
                            { launchImagePicker(forAlbum = true) }
                        } else {
                            null
                        },
                        albumHasArt = currentAlbum?.artworkUri != null,
                        // Not offered on "No album" itself — it is already the destination.
                        onTreatAsNoAlbum = if (isAlbum && !isNoAlbumSource) viewModel::treatAlbumAsNoAlbum else null,
                        onRemove = if (isUserPlaylist) viewModel::promptRemovePlaylist else null,
                    )
                }

                // Once the hero's buttons scroll away, a FAB keeps "play" at hand.
                AnimatedVisibility(
                    visible = collapsed && !state.inSelectionMode && state.songs.isNotEmpty(),
                    enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.l),
                ) {
                    FloatingActionButton(
                        onClick = {
                            haptics.confirm()
                            viewModel.playAll()
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        AppIcon(AppSymbols.PlayArrow, contentDescription = stringResource(R.string.action_play_all), filled = true)
                    }
                }
            }

            SelectionChrome(
                visible = state.inSelectionMode,
                selectedCount = state.selectedIds.size,
                allSelectedFavorite = state.allSelectedAreFavorite,
                singleSelectionHasArt = state.selectedIds.size == 1 &&
                    state.songs.firstOrNull { it.id in state.selectedIds }?.albumArtUri != null,
                showRemoveFromPlaylist = isUserPlaylist,
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onToggleFavorites = viewModel::toggleFavoritesForSelection,
                onAddToQueue = viewModel::addSelectionToQueue,
                onAddToPlaylist = viewModel::startAddToPlaylist,
                onEditTags = viewModel::startEditTags,
                onDecompose = viewModel::startDecompose,
                onChangeCoverArt = { launchImagePicker(forAlbum = false) },
                onRemoveFromPlaylist = viewModel::removeSelectionFromPlaylist,
                onShare = {
                    shareSongs(context, state.songs.filter { it.id in state.selectedIds })
                    viewModel.clearSelection()
                },
                onShowInfo = viewModel::startFileInfo,
                // In a tab the bar isn't at the screen top, so drop the status-bar inset.
                windowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                onTopBarHeightChanged = { selectionBarHeightPx = it },
            )

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }

        when (val current = dialog) {
            is SongDialog.EditTags -> EditTagsDialog(
                songs = current.songs,
                onDismiss = viewModel::dismissDialog,
                onSave = { edit -> viewModel.saveTags(current.songs, edit) },
            )

            is SongDialog.Decompose -> DecomposeTitleDialog(
                songs = current.songs,
                onDismiss = viewModel::dismissDialog,
                onApply = { options -> viewModel.applyDecomposition(current.songs, options) },
            )

            is SongDialog.AddToPlaylist -> AddToPlaylistDialog(
                playlists = userPlaylists,
                onDismiss = viewModel::dismissDialog,
                onSelect = { id -> viewModel.addToExistingPlaylist(id, current.songs) },
                onCreateNew = { name -> viewModel.createPlaylistAndAdd(name, current.songs) },
            )

            is SongDialog.AddSongToPlaylist -> SongPickerDialog(
                title = stringResource(R.string.action_add_to_playlist),
                songs = allSongs,
                onDismiss = viewModel::dismissDialog,
                onPick = viewModel::addSongToCurrentPlaylist,
            )

            is SongDialog.EditAlbum -> EditAlbumDialog(
                dialog = current,
                onDismiss = viewModel::dismissDialog,
                onSave = { t, y, custom, cover, author -> viewModel.saveAlbumEdit(t, y, custom, cover, author) },
            )

            is SongDialog.EditArtist -> EditArtistDialog(
                initialName = current.name,
                onDismiss = viewModel::dismissDialog,
                onSave = viewModel::saveArtistName,
            )

            is SongDialog.EditPlaylist -> PlaylistNameDialog(
                titleRes = R.string.playlist_rename,
                confirmRes = R.string.playlist_rename_confirm,
                initialName = current.currentName,
                onDismiss = viewModel::dismissDialog,
                onConfirm = viewModel::savePlaylistName,
            )

            is SongDialog.ConfirmRemovePlaylist -> FormSheet(
                title = stringResource(R.string.playlist_delete_title),
                symbol = AppSymbols.Delete,
                supportingText = stringResource(R.string.playlist_delete_message, current.name),
                onDismiss = viewModel::dismissDialog,
                dismissLabel = stringResource(R.string.action_cancel),
                confirmLabel = stringResource(R.string.action_remove),
                destructive = true,
                onConfirm = viewModel::removeCurrentPlaylist,
            )

            is SongDialog.ConfirmAlbumRetag -> ConfirmRetagSheet(
                count = current.count,
                onConfirm = { viewModel.confirmAlbumRetag(current.edit, current.songs) },
                onDismiss = viewModel::dismissDialog,
            )

            is SongDialog.ConfirmArtistRetag -> ConfirmRetagSheet(
                count = current.count,
                onConfirm = { viewModel.confirmArtistRetag(current.edits) },
                onDismiss = viewModel::dismissDialog,
            )

            is SongDialog.AlbumCoverArtTarget -> FormSheet(
                title = stringResource(R.string.cover_art_target_title),
                symbol = AppSymbols.Image,
                supportingText = stringResource(R.string.cover_art_target_message),
                onDismiss = viewModel::dismissDialog,
                // "First song only" is the gentler choice, so it takes the secondary slot.
                dismissLabel = stringResource(R.string.cover_art_target_first),
                confirmLabel = stringResource(R.string.cover_art_target_all),
                onConfirm = { viewModel.applyAlbumCoverArt(current.artwork, toAllSongs = true) },
                onDismissButton = { viewModel.applyAlbumCoverArt(current.artwork, toAllSongs = false) },
            )

            is SongDialog.FileInfo -> FileInfoDialog(
                infos = current.infos,
                onDismiss = viewModel::dismissDialog,
            )

            null -> Unit
        }
    }
}

/** What the hero shows above the title. */
private sealed interface HeroVisual {
    data class Art(val model: Any?, val sharedKey: String?) : HeroVisual
    data class Artist(val name: String) : HeroVisual
    data class Symbol(val symbol: AppSymbol, val polygon: androidx.graphics.shapes.RoundedPolygon) : HeroVisual
}

/**
 * The page's hero: a big cover (glowing with its own colors, and flying in from the grid on album
 * pages), an artist's shaped monogram or a symbol; then the title, author, metadata pills and a
 * Play / Shuffle pair.
 */
@Composable
private fun SongListHero(
    title: String,
    visual: HeroVisual,
    shape: Shape,
    author: String?,
    year: Int,
    songCount: Int,
    totalDurationMs: Long,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 64.dp, start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        when (visual) {
            is HeroVisual.Art -> Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
            ) {
                // A soft glow in the cover's own (themed) primary rather than a grey drop shadow.
                AlbumArt(
                    model = visual.model,
                    shape = shape,
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = shape,
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .then(if (visual.sharedKey != null) Modifier.sharedElement(visual.sharedKey, shape) else Modifier),
                )
            }

            is HeroVisual.Artist -> ArtistAvatar(
                name = visual.name,
                size = 168.dp,
                textStyle = MaterialTheme.typography.displayMediumEmphasized,
            )

            is HeroVisual.Symbol -> ShapedIcon(
                symbol = visual.symbol,
                polygon = visual.polygon,
                size = 168.dp,
                iconSize = 72.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!author.isNullOrBlank()) {
            Text(
                text = author,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (year > 0) MetaPill(year.toString())
            MetaPill(pluralStringResource(R.plurals.song_count, songCount, songCount))
            MetaPill(formatDuration(totalDurationMs))
        }
        PlayShuffleRow(onPlay = onPlay, onShuffle = onShuffle, modifier = Modifier.padding(top = Spacing.s))
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
        )
    }
}

/** Play all (filled) and Shuffle (tonal), side by side and equally wide. */
@Composable
private fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(
            onClick = {
                haptics.confirm()
                onPlay()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            AppIcon(AppSymbols.PlayArrow, contentDescription = null, filled = true, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.action_play), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        FilledTonalButton(
            onClick = {
                haptics.confirm()
                onShuffle()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            AppIcon(AppSymbols.Shuffle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.action_shuffle), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/**
 * The page's top bar: transparent over the hero, it fills in and shows the title once the hero
 * scrolls away. Queue and playlist actions sit up front, editing lives in the overflow.
 */
@Composable
private fun SongListTopBar(
    title: String,
    collapsed: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onChangeCoverArt: (() -> Unit)?,
    albumHasArt: Boolean,
    onTreatAsNoAlbum: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val containerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "topBarColor",
    )
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onEdit != null || onChangeCoverArt != null || onTreatAsNoAlbum != null || onRemove != null

    TopAppBar(
        title = {
            AnimatedVisibility(visible = collapsed, enter = fadeIn(), exit = fadeOut()) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { if (showBack) BackButton(onBack) },
        actions = {
            IconButton(onClick = onAddToQueue, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.QueueMusic, contentDescription = stringResource(R.string.action_add_to_queue))
            }
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.PlaylistAdd, contentDescription = stringResource(R.string.action_add_to_playlist))
                }
            }
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.MoreVert, stringResource(R.string.action_more))
                    }
                    AppMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val hasDestructive = onRemove != null
                        AppMenuGroup(index = 0, count = if (hasDestructive) 2 else 1) {
                            onEdit?.let {
                                AppMenuItem(
                                    text = stringResource(R.string.action_edit),
                                    symbol = AppSymbols.Edit,
                                    onClick = { menuExpanded = false; it() },
                                )
                            }
                            onChangeCoverArt?.let {
                                AppMenuItem(
                                    text = stringResource(
                                        if (albumHasArt) R.string.action_change_cover_art else R.string.action_add_cover_art,
                                    ),
                                    symbol = AppSymbols.Image,
                                    onClick = { menuExpanded = false; it() },
                                )
                            }
                            onTreatAsNoAlbum?.let {
                                AppMenuItem(
                                    text = stringResource(R.string.action_treat_as_no_album),
                                    symbol = AppSymbols.FolderOff,
                                    onClick = { menuExpanded = false; it() },
                                )
                            }
                        }
                        onRemove?.let {
                            AppMenuGroup(index = 1, count = 2) {
                                AppMenuItem(
                                    text = stringResource(R.string.action_remove),
                                    symbol = AppSymbols.Delete,
                                    destructive = true,
                                    onClick = { menuExpanded = false; it() },
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
    )
}

/** The albums a performer authored, newest first, as an uncontained M3 carousel. */
@Composable
private fun AuthoredAlbumsSection(
    albums: List<Album>,
    shape: Shape,
    onAlbumClick: (Long) -> Unit,
    coverArtVersion: Int = 0,
) {
    Column(Modifier.padding(bottom = Spacing.s)) {
        SectionLabel(stringResource(R.string.albums_authored_section))
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { albums.size },
            itemWidth = 148.dp,
            itemSpacing = Spacing.m,
            contentPadding = PaddingValues(horizontal = Spacing.s),
            modifier = Modifier.fillMaxWidth(),
        ) { index ->
            val album = albums[index]
            Column(
                Modifier
                    .width(148.dp)
                    .maskClip(MaterialTheme.shapes.large)
                    .clickable { onAlbumClick(album.id) },
            ) {
                AlbumArt(
                    model = album.coverArtModel(coverArtVersion),
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElement(albumArtKey(album.id), shape),
                )
                Text(
                    text = album.title.ifBlank { stringResource(R.string.unknown_album) },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs),
                )
                if (album.year > 0) {
                    Text(
                        text = album.year.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.m, top = Spacing.l, bottom = Spacing.s, end = Spacing.m),
    )
}

@Composable
private fun ConfirmRetagSheet(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FormSheet(
        title = stringResource(R.string.retag_confirm_title),
        symbol = AppSymbols.Label,
        supportingText = pluralStringResource(R.plurals.retag_confirm_message, count, count),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_cancel),
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = onConfirm,
    )
}

@Composable
private fun headerTitle(
    source: SongListSource,
    songs: List<Song>,
    userPlaylists: List<UserPlaylist>,
): String = when (source) {
    is SongListSource.AlbumSource ->
        if (source.albumId == NO_ALBUM_ID) {
            stringResource(R.string.no_album)
        } else {
            songs.firstOrNull()?.album?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.unknown_album)
        }

    is SongListSource.PerformerSource -> source.name
    is SongListSource.GenreSource -> source.name
    is SongListSource.PlaylistSource -> stringResource(systemPlaylistNameRes(source.playlist))
    is SongListSource.UserPlaylistSource ->
        userPlaylists.firstOrNull { it.id == source.playlistId }?.name.orEmpty()
}
