package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.domain.model.RepeatMode
import pl.dakil.music.domain.model.Song
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.TimeBar
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.lyrics.LrclibSheet
import pl.dakil.music.presentation.lyrics.LyricsAlignSheet
import pl.dakil.music.presentation.lyrics.LyricsEventsEffect
import pl.dakil.music.presentation.lyrics.LyricsPane
import pl.dakil.music.presentation.lyrics.LyricsViewModel
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sin

private enum class PlayerSheetKind { SLEEP, SPEED, EQUALIZER, ALIGN, LRCLIB }

/**
 * The expanded player. The cover sits in a pager over the queue (swipe to skip) and flips over to
 * synced lyrics; under it the song, a wavy seek bar, an expressive transport row and a strip of
 * tools that open sheets (sleep timer, speed, equalizer) and the queue panel.
 *
 * Vertical drags follow the finger: down anywhere but the lyrics closes the player ([sheetDrag]),
 * up on the cover flips it to the lyrics, and up on the controls raises the queue.
 * [onArtSlotPlaced] reports where the cover sits so the sheet can fly the art between the mini and
 * the full player.
 */
@Composable
fun FullPlayer(
    viewModel: NowPlayingViewModel,
    state: NowPlayingUiState,
    artVisible: Boolean,
    onArtSlotPlaced: (LayoutCoordinates?) -> Unit,
    sheetDrag: DragTarget,
    onCollapse: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val haptics = rememberHaptics()
    val motion = MaterialTheme.motionScheme
    val lyricsCard by viewModel.lyrics.collectAsStateWithLifecycle()
    val equalizer by viewModel.equalizer.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val showAddToPlaylist by viewModel.showAddToPlaylist.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val flipSpec = motion.slowSpatialSpec<Float>()
    val panelSpec = motion.defaultSpatialSpec<Float>()

    // The cover's turn toward the lyrics on its back: 0 shows the cover, 1 the lyrics. A drag up
    // the cover turns it under the finger; the lyrics button turns it on its own.
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val flip = remember { GestureProgress(if (showLyrics) 1f else 0f) }
    flip.spec = flipSpec
    fun setLyrics(show: Boolean) {
        showLyrics = show
        scope.launch { flip.animateTo(if (show) 1f else 0f) }
    }
    LaunchedEffect(lyricsCard.visible) { if (!lyricsCard.visible && flip.targetValue != 0f) setLyrics(false) }
    var artHeight by remember { mutableFloatStateOf(1f) }
    val flipDrag = flip.rememberDragTarget(scope, travel = { artHeight * 0.6f }) { target ->
        if ((target == 1f) != showLyrics) haptics.toggle(target == 1f)
        showLyrics = target == 1f
    }
    val turning by remember { derivedStateOf { flip.value > 0f } }
    val lyricsSide by remember { derivedStateOf { flip.value >= 0.5f } }

    val queuePanel = remember { GestureProgress(0f) }
    queuePanel.spec = panelSpec
    var panelTravel by remember { mutableFloatStateOf(1f) }
    val queueDrag = queuePanel.rememberDragTarget(scope, travel = { panelTravel }) { target ->
        if (target != queuePanel.value) haptics.tick()
    }

    // Down closes the player wherever it's grabbed; up has a job of its own on the cover (when
    // there are lyrics to turn to) and on the controls.
    val headerDrag = rememberVerticalDrag(up = sheetDrag, down = sheetDrag)
    val artDrag = rememberVerticalDrag(up = flipDrag.takeIf { lyricsCard.visible }, down = sheetDrag, enabled = !showLyrics)
    val controlsDrag = rememberVerticalDrag(up = queueDrag.takeIf { state.queue.isNotEmpty() }, down = sheetDrag)

    var openSheet by remember { mutableStateOf<PlayerSheetKind?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The lyrics view model only spins up while lyrics are on screen, turning in, or being edited.
    val lyricsViewModel: LyricsViewModel? =
        if (showLyrics || turning || openSheet == PlayerSheetKind.ALIGN || openSheet == PlayerSheetKind.LRCLIB) {
            viewModel(factory = AppViewModelProvider.Factory)
        } else {
            null
        }
    val lyricsState = lyricsViewModel?.state?.collectAsStateWithLifecycle()?.value
    lyricsViewModel?.let { LyricsEventsEffect(it, snackbarHostState) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { panelTravel = it.height.toFloat() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            PlayerHeader(
                state = state,
                showLyrics = showLyrics,
                lyricsSynced = lyricsState?.synced == true,
                lrclibEnabled = lyricsState?.lrclibEnabled == true,
                canBurn = lyricsState?.canBurn == true,
                onCollapse = onCollapse,
                onAddToPlaylist = viewModel::openAddToPlaylist,
                onOpenAlbum = { onOpenAlbum(song.albumId) },
                onOpenArtist = song.artists.firstOrNull()?.let { artist -> { onOpenArtist(artist) } },
                onAlign = { openSheet = PlayerSheetKind.ALIGN },
                onLrclib = { openSheet = PlayerSheetKind.LRCLIB },
                onBurn = { lyricsViewModel?.burn() },
                modifier = headerDrag,
            )

            // Portrait stacks the cover over the controls; a wide window (landscape phone,
            // tablet) puts them side by side.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                val artArea: @Composable (Modifier) -> Unit = { artModifier ->
                    BoxWithConstraints(
                        modifier = artModifier
                            .onSizeChanged { artHeight = it.height.toFloat().coerceAtLeast(1f) }
                            .then(artDrag),
                        contentAlignment = Alignment.Center,
                    ) {
                        val side = minOf(maxWidth - Spacing.xl * 2, maxHeight - Spacing.l)
                        // Both faces turn about the horizontal axis, the cover's lower edge rising
                        // toward the viewer; each shows only on its own half of the turn.
                        if (!lyricsSide) {
                            ArtPager(
                                state = state,
                                side = side,
                                artVisible = artVisible,
                                onArtSlotPlaced = onArtSlotPlaced,
                                onSkipTo = {
                                    haptics.tick()
                                    viewModel.onQueueItemClick(it)
                                },
                                modifier = Modifier.flipFace(flip, back = false),
                            )
                        } else {
                            LaunchedEffect(Unit) { onArtSlotPlaced(null) }
                            Box(Modifier.fillMaxSize().flipFace(flip, back = true), contentAlignment = Alignment.Center) {
                                if (lyricsState != null) {
                                    LyricsPane(
                                        state = lyricsState,
                                        onSeekLine = { lyricsViewModel?.onSeekToLine(it) },
                                        onSearchLrclib = if (lyricsState.lrclibEnabled) {
                                            { openSheet = PlayerSheetKind.LRCLIB }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    LoadingIndicator()
                                }
                            }
                        }
                    }
                }
                val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
                    Column(controlsModifier.then(controlsDrag)) {
                        SongInfo(
                            song = song,
                            index = state.currentIndex,
                            isFavorite = state.isCurrentFavorite,
                            onToggleFavorite = viewModel::onToggleFavorite,
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                        )
                        Spacer(Modifier.height(Spacing.s))
                        TimeBar(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            onSeek = viewModel::onSeek,
                            isPlaying = state.isPlaying,
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                        )
                        Spacer(Modifier.height(Spacing.s))
                        TransportRow(
                            state = state,
                            onPlayPause = viewModel::onPlayPause,
                            onPrevious = viewModel::onPrevious,
                            onNext = viewModel::onNext,
                            onToggleShuffle = viewModel::onToggleShuffle,
                            onCycleRepeat = viewModel::onCycleRepeat,
                            modifier = Modifier.padding(horizontal = Spacing.l),
                        )
                        Spacer(Modifier.height(Spacing.l))
                        ToolsRow(
                            state = state,
                            lyricsAvailable = lyricsCard.visible,
                            showLyrics = showLyrics,
                            equalizerOn = equalizer.capabilities.available && equalizer.settings.masterEnabled,
                            onToggleLyrics = {
                                haptics.toggle(!showLyrics)
                                setLyrics(!showLyrics)
                            },
                            onOpen = { openSheet = it },
                            onOpenQueue = { scope.launch { queuePanel.animateTo(1f) } },
                            modifier = Modifier.padding(horizontal = Spacing.l),
                        )
                        Spacer(Modifier.height(Spacing.l))
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        artArea(Modifier.weight(1f).fillMaxHeight())
                        controls(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        artArea(Modifier.weight(1f).fillMaxWidth())
                        controls(Modifier)
                    }
                }
            }
        }

        QueuePanel(
            progress = queuePanel,
            queue = state.queue,
            currentIndex = state.currentIndex,
            isPlaying = state.isPlaying,
            removeMode = state.queueRemoveMode,
            onItemClick = viewModel::onQueueItemClick,
            onMove = viewModel::onMoveQueueItem,
            onRemove = viewModel::onRemoveQueueItem,
            onClear = viewModel::onClearQueue,
        )

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    when (openSheet) {
        PlayerSheetKind.SLEEP -> SleepTimerSheet(
            remainingMs = state.sleepTimerRemainingMs,
            mode = state.sleepTimerMode,
            onStart = viewModel::onStartSleepTimer,
            onEndOfTrack = viewModel::onStartSleepTimerEndOfTrack,
            onEndOfQueue = viewModel::onStartSleepTimerEndOfQueue,
            onCancel = viewModel::onCancelSleepTimer,
            onDismiss = { openSheet = null },
        )
        PlayerSheetKind.SPEED -> SpeedSheet(
            speed = state.playbackSpeed,
            onSetSpeed = viewModel::onSetSpeed,
            onDismiss = { openSheet = null },
        )
        PlayerSheetKind.EQUALIZER -> EqualizerSheet(
            state = equalizer,
            onDismiss = { openSheet = null },
            onMasterEnabledChange = viewModel::onSetEqMasterEnabled,
            onSelectPreset = viewModel::onSelectEqPreset,
            onBandLevel = viewModel::onSetEqBandLevel,
            onBassBoost = viewModel::onSetBassBoost,
            onReset = viewModel::onResetEqualizer,
        )
        PlayerSheetKind.ALIGN -> lyricsState?.let {
            LyricsAlignSheet(
                offsetMs = it.offsetMs,
                onOffsetChange = { offset -> lyricsViewModel?.setOffset(offset) },
                onNudge = { delta -> lyricsViewModel?.nudgeOffset(delta) },
                onDismiss = { openSheet = null },
            )
        }
        PlayerSheetKind.LRCLIB -> lyricsState?.let {
            LrclibSheet(
                defaultArtist = it.defaultArtist,
                defaultTrack = it.defaultTrack,
                matches = it.matches,
                searching = it.lrclibSearching,
                onSearch = { artist, track -> lyricsViewModel?.search(artist, track) },
                onSelect = { match ->
                    haptics.confirm()
                    lyricsViewModel?.selectMatch(match)
                    openSheet = null
                },
                onDismiss = { openSheet = null },
            )
        }
        null -> Unit
    }

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
private fun PlayerHeader(
    state: NowPlayingUiState,
    showLyrics: Boolean,
    lyricsSynced: Boolean,
    lrclibEnabled: Boolean,
    canBurn: Boolean,
    onCollapse: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    onAlign: () -> Unit,
    onLrclib: () -> Unit,
    onBurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val song = state.song
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
            AppIcon(AppSymbols.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (showLyrics) R.string.lyrics_title else R.string.now_playing_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.queue.isNotEmpty() && state.currentIndex >= 0) {
                Text(
                    stringResource(R.string.player_queue_position, state.currentIndex + 1, state.queue.size),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val lyricsTools = showLyrics && (lyricsSynced || lrclibEnabled || canBurn)
                AppMenuGroup(index = 0, count = if (lyricsTools) 2 else 1) {
                    AppMenuItem(
                        text = stringResource(R.string.action_add_to_playlist),
                        symbol = AppSymbols.PlaylistAdd,
                        onClick = {
                            menuOpen = false
                            onAddToPlaylist()
                        },
                    )
                    if (song != null && !song.treatedAsNoAlbum && song.album.isNotBlank()) {
                        AppMenuItem(
                            text = stringResource(R.string.player_go_to_album),
                            symbol = AppSymbols.Album,
                            onClick = {
                                menuOpen = false
                                onOpenAlbum()
                            },
                        )
                    }
                    if (onOpenArtist != null) {
                        AppMenuItem(
                            text = stringResource(R.string.player_go_to_artist),
                            symbol = AppSymbols.Person,
                            onClick = {
                                menuOpen = false
                                onOpenArtist()
                            },
                        )
                    }
                }
                if (lyricsTools) {
                    AppMenuGroup(index = 1, count = 2) {
                        if (lyricsSynced) {
                            AppMenuItem(
                                text = stringResource(R.string.lyrics_align),
                                symbol = AppSymbols.Timer,
                                onClick = {
                                    menuOpen = false
                                    onAlign()
                                },
                            )
                        }
                        if (lrclibEnabled) {
                            AppMenuItem(
                                text = stringResource(R.string.lyrics_select_lrclib),
                                symbol = AppSymbols.CloudDownload,
                                onClick = {
                                    menuOpen = false
                                    onLrclib()
                                },
                            )
                        }
                        if (canBurn) {
                            AppMenuItem(
                                text = stringResource(R.string.lyrics_burn),
                                symbol = AppSymbols.Save,
                                onClick = {
                                    menuOpen = false
                                    onBurn()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The cover in a pager over the whole queue: neighbours peek in at the sides, swiping settles on a
 * song and plays it. Follows the player when it skips on its own. The current cover eases back a
 * little while paused.
 */
@Composable
private fun ArtPager(
    state: NowPlayingUiState,
    side: androidx.compose.ui.unit.Dp,
    artVisible: Boolean,
    onArtSlotPlaced: (LayoutCoordinates?) -> Unit,
    onSkipTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = state.queue
    val pagerState = rememberPagerState(initialPage = state.currentIndex.coerceAtLeast(0)) { queue.size }
    val currentIndex by rememberUpdatedState(state.currentIndex)
    val currentOnSkipTo by rememberUpdatedState(onSkipTo)

    // Follow skips made elsewhere (transport buttons, queue, notification).
    LaunchedEffect(state.currentIndex, queue.size) {
        val target = state.currentIndex
        if (target in 0 until queue.size && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            if (abs(target - pagerState.currentPage) > 3) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }

    // A swipe that settles on another cover plays that song.
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userSwiped by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) userSwiped = true }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (userSwiped) {
                userSwiped = false
                if (page != currentIndex && page in queue.indices) currentOnSkipTo(page)
            }
        }
    }

    val restScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.9f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "artRest",
    )
    val shape = RoundedCornerShape(state.coverArtRoundnessDp.dp)

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // An invisible slot marking where the current cover rests, for the sheet's flying art.
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = restScale
                    scaleY = restScale
                }
                .onGloballyPositioned { onArtSlotPlaced(it) },
        )
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(side),
            contentPadding = PaddingValues(horizontal = (maxWidth - side) / 2),
            pageSpacing = Spacing.l,
            beyondViewportPageCount = 1,
            key = { index -> queue.getOrNull(index)?.let { "${it.id}@$index" } ?: index },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val song = queue.getOrNull(page) ?: return@HorizontalPager
            AlbumArt(
                model = song.coverArtModel(),
                shape = shape,
                modifier = Modifier
                    .size(side)
                    .graphicsLayer {
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        val distance = offset.coerceIn(0f, 1f)
                        val scale = lerp(restScale, 0.82f, distance)
                        scaleX = scale
                        scaleY = scale
                        alpha = if (page == state.currentIndex && !artVisible) 0f else lerp(1f, 0.6f, distance)
                    },
            )
        }
    }
}

@Composable
private fun SongInfo(
    song: Song,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val motion = MaterialTheme.motionScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = song to index,
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion.fastSpatialSpec()) { if (forward) -it / 2 else it / 2 } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            contentKey = { it.first.id },
            modifier = Modifier.weight(1f),
            label = "songInfo",
        ) { (current, _) ->
            Column {
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                )
                Text(
                    current.artists.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FavoriteButton(
            isFavorite = isFavorite,
            onToggle = {
                if (!isFavorite) haptics.confirm() else haptics.toggle(false)
                onToggleFavorite()
            },
        )
    }
}

/**
 * One face of the cover's flip: the front turns away over the first half of [flip], the back
 * (lyrics) turns in over the second, sinking a little at the edge-on midpoint for depth.
 */
private fun Modifier.flipFace(flip: GestureProgress, back: Boolean): Modifier = graphicsLayer {
    val p = flip.value
    rotationX = if (back) (p - 1f) * 180f else p * 180f
    cameraDistance = 14f * density
    val sink = 1f - 0.08f * sin(PI.toFloat() * p.coerceIn(0f, 1f))
    scaleX = sink
    scaleY = sink
}

/** A heart that pops with a little spring overshoot when it fills. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(isFavorite) }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(isFavorite) {
        if (isFavorite && !previous) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 600f))
        } else if (!isFavorite && previous) {
            pop.animateTo(1f, spring)
        }
        previous = isFavorite
    }
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
    ) {
        AnimatedAppIcon(
            AppSymbols.Favorite,
            contentDescription = stringResource(
                if (isFavorite) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites,
            ),
            filled = isFavorite,
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(IconButtonDefaults.mediumIconSize)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

/**
 * Shuffle · previous · play/pause · next · repeat. The middle three widen while pressed (squeezing
 * their neighbours) and play/pause morphs between a pill and a rounded square.
 */
@Composable
private fun TransportRow(
    state: NowPlayingUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val motion = MaterialTheme.motionScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        IconToggleButton(
            checked = state.shuffleEnabled,
            onCheckedChange = {
                haptics.toggle(it)
                onToggleShuffle()
            },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            AnimatedAppIcon(
                AppSymbols.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                filled = state.shuffleEnabled,
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WideningButton(baseWeight = 1f) { interaction ->
            FilledTonalIconButton(
                onClick = {
                    haptics.tick()
                    onPrevious()
                },
                enabled = state.hasPrevious,
                shapes = IconButtonDefaults.shapes(),
                interactionSource = interaction,
                modifier = Modifier.fillMaxSize(0.78f).fillMaxWidth(),
            ) {
                AppIcon(AppSymbols.SkipPrevious, contentDescription = stringResource(R.string.cd_previous), filled = true, modifier = Modifier.size(32.dp))
            }
        }
        WideningButton(baseWeight = 1.5f) { interaction ->
            FilledIconToggleButton(
                checked = state.isPlaying,
                onCheckedChange = {
                    haptics.toggle(it)
                    onPlayPause()
                },
                shapes = IconButtonDefaults.toggleableShapes(
                    shape = RoundedCornerShape(50),
                    pressedShape = RoundedCornerShape(20.dp),
                    checkedShape = RoundedCornerShape(28.dp),
                ),
                interactionSource = interaction,
                modifier = Modifier.fillMaxSize(),
            ) {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn()) togetherWith
                            (scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut())
                    },
                    label = "playPause",
                ) { playing ->
                    AppIcon(
                        if (playing) AppSymbols.Pause else AppSymbols.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                        filled = true,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        WideningButton(baseWeight = 1f) { interaction ->
            FilledTonalIconButton(
                onClick = {
                    haptics.tick()
                    onNext()
                },
                enabled = state.hasNext,
                shapes = IconButtonDefaults.shapes(),
                interactionSource = interaction,
                modifier = Modifier.fillMaxSize(0.78f).fillMaxWidth(),
            ) {
                AppIcon(AppSymbols.SkipNext, contentDescription = stringResource(R.string.cd_next), filled = true, modifier = Modifier.size(32.dp))
            }
        }
        val repeatOn = state.repeatMode != RepeatMode.OFF
        IconToggleButton(
            checked = repeatOn,
            onCheckedChange = {
                haptics.toggle(state.repeatMode != RepeatMode.ONE)
                onCycleRepeat()
            },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            AnimatedAppIcon(
                if (state.repeatMode == RepeatMode.ONE) AppSymbols.RepeatOne else AppSymbols.Repeat,
                contentDescription = stringResource(R.string.cd_repeat),
                filled = repeatOn,
                tint = if (repeatOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A transport slot whose weight grows while its button is held, the M3 Expressive press-to-widen. */
@Composable
private fun RowScope.WideningButton(
    baseWeight: Float,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * 1.25f else baseWeight,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "widen",
    )
    Box(Modifier.weight(weight).fillMaxSize(), contentAlignment = Alignment.Center) { content(interaction) }
}

/** The strip of player tools; active ones fill and show what they're set to. */
@Composable
private fun ToolsRow(
    state: NowPlayingUiState,
    lyricsAvailable: Boolean,
    showLyrics: Boolean,
    equalizerOn: Boolean,
    onToggleLyrics: () -> Unit,
    onOpen: (PlayerSheetKind) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerLabel = when {
        state.sleepTimerRemainingMs != null -> formatDuration(state.sleepTimerRemainingMs)
        state.sleepTimerMode != null -> stringResource(
            if (state.sleepTimerMode == pl.dakil.music.domain.model.SleepTimerMode.END_OF_TRACK) {
                R.string.sleep_end_of_track_short
            } else {
                R.string.sleep_end_of_queue_short
            },
        )
        else -> null
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lyricsAvailable) {
            ToolButton(AppSymbols.Lyrics, stringResource(R.string.lyrics_title), active = showLyrics, onClick = onToggleLyrics)
        }
        ToolButton(
            AppSymbols.Bedtime,
            stringResource(R.string.sleep_timer),
            active = timerLabel != null,
            label = timerLabel,
            onClick = { onOpen(PlayerSheetKind.SLEEP) },
        )
        ToolButton(
            AppSymbols.Speed,
            stringResource(R.string.speed_title),
            active = state.playbackSpeed != 1f,
            label = if (state.playbackSpeed != 1f) formatSpeed(state.playbackSpeed) else null,
            onClick = { onOpen(PlayerSheetKind.SPEED) },
        )
        ToolButton(AppSymbols.GraphicEq, stringResource(R.string.eq_open), active = equalizerOn, onClick = { onOpen(PlayerSheetKind.EQUALIZER) })
        ToolButton(AppSymbols.QueueMusic, stringResource(R.string.now_playing_queue), active = false, onClick = onOpenQueue)
    }
}

@Composable
private fun ToolButton(
    symbol: AppSymbol,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    label: String? = null,
) {
    val haptics = rememberHaptics()
    ToggleButton(
        checked = active,
        onCheckedChange = {
            haptics.tick()
            onClick()
        },
        colors = ToggleButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.m),
    ) {
        AnimatedAppIcon(symbol, contentDescription = description, filled = active, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = label != null,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Row {
                Spacer(Modifier.width(Spacing.xs))
                Text(label.orEmpty(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
