package pl.dakil.music.presentation.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Genre
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.ArtistAvatar
import pl.dakil.music.presentation.components.FileInfoDialog
import pl.dakil.music.presentation.components.NoAlbumArt
import pl.dakil.music.presentation.components.SelectionChrome
import pl.dakil.music.presentation.components.SongLeading
import pl.dakil.music.presentation.components.SongRow
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.presentation.components.shareSongs
import pl.dakil.music.presentation.components.stableIndex
import pl.dakil.music.presentation.components.tonalPairFor
import pl.dakil.music.presentation.navigation.navItemUi
import pl.dakil.music.presentation.playlist.AddToPlaylistDialog
import pl.dakil.music.presentation.playlist.PlaylistNameDialog
import pl.dakil.music.presentation.songlist.DecomposeTitleDialog
import pl.dakil.music.presentation.songlist.EditTagsDialog
import pl.dakil.music.presentation.songlist.SystemPlaylistSongListScreen
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.AppMenuItem
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.entrance
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.pressScale
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.designsystem.sharedElement
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing

@StringRes
fun systemPlaylistNameRes(playlist: SystemPlaylist): Int = when (playlist) {
    SystemPlaylist.ALL_SONGS -> R.string.playlist_all_songs
    SystemPlaylist.FAVORITES -> R.string.playlist_favorites
}

/** Shared-element key of an album's cover, matched by the album page's header. */
fun albumArtKey(albumId: Long) = "album-art-$albumId"

/** Items further down than this don't play the entrance cascade (they'd wait too long). */
private const val ENTRANCE_ITEMS = 12

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tabs by viewModel.libraryTabs.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { tabs.size }

    // Hoisted so re-tapping the Library tab can scroll the visible page to the top.
    val albumsGridState = rememberLazyGridState()
    val performersListState = rememberLazyListState()
    val genresGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val albumColumns by viewModel.albumColumns.collectAsStateWithLifecycle()
    val albumCornerDp by viewModel.albumCornerDp.collectAsStateWithLifecycle()
    val performers by viewModel.performers.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
    val genreSort by viewModel.genreSort.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedSongIds by viewModel.selectedSongIds.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val songDialog by viewModel.dialog.collectAsStateWithLifecycle()
    val coverArtVersion by viewModel.coverArtVersion.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val searching = searchActive || query.isNotEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onWritePermissionGranted()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onCoverArtPicked(context.contentResolver, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryEvent.Message ->
                    snackbarHostState.showSnackbar(resources.getString(event.res))

                is LibraryEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
        }
    }

    val allSongsName = stringResource(R.string.playlist_all_songs)
    val favoritesName = stringResource(R.string.playlist_favorites)
    LaunchedEffect(allSongsName, favoritesName) {
        viewModel.setSystemPlaylistNames(
            mapOf(
                SystemPlaylist.ALL_SONGS to allSongsName,
                SystemPlaylist.FAVORITES to favoritesName,
            ),
        )
    }

    fun closeSearch() {
        viewModel.clearQuery()
        searchActive = false
        focusManager.clearFocus()
    }

    // Back exits search instead of leaving the app — but only once the keyboard is
    // down. While the IME is up, back is left to the system so it closes the keyboard
    // first. Covers button, gesture and predictive back alike.
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = selectedSongIds.isNotEmpty()) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = selectedSongIds.isEmpty() && searching && !imeVisible) {
        closeSearch()
    }

    // Re-tapping the Library tab exits an active search, otherwise scrolls the
    // currently visible page back to the top.
    LaunchedEffect(onReselect) {
        onReselect.collect {
            if (viewModel.query.value.isNotEmpty() || searchActive) {
                closeSearch()
            } else {
                when (tabs.getOrNull(pagerState.currentPage)) {
                    NavItem.ALBUMS -> albumsGridState.animateScrollToItem(0)
                    NavItem.ARTISTS -> performersListState.animateScrollToItem(0)
                    NavItem.GENRES -> genresGridState.animateScrollToItem(0)
                    NavItem.PLAYLISTS -> playlistsListState.animateScrollToItem(0)
                    else -> Unit
                }
            }
        }
    }

    // A tick as the pager settles on another tab, however it got there.
    LaunchedEffect(pagerState) {
        var last = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != last) haptics.tick()
            last = page
        }
    }

    // The first screenful cascades in once; later recompositions (scrolling back up,
    // returning from an album) don't replay it.
    var cascadeDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_200)
        cascadeDone = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(
                searching = searching,
                query = query,
                subtitle = librarySubtitle(albums.size, performers.size),
                onQueryChange = viewModel::onQueryChange,
                onOpenSearch = { searchActive = true },
                onCloseSearch = ::closeSearch,
            )

            AnimatedContent(
                targetState = query.isBlank(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "libraryBody",
            ) { browsing ->
                if (browsing) {
                    // Keep the selected page valid when the tab set shrinks via customization.
                    LaunchedEffect(tabs.size) {
                        if (pagerState.currentPage >= tabs.size && tabs.isNotEmpty()) {
                            pagerState.scrollToPage(tabs.size - 1)
                        }
                    }
                    if (tabs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(AppSymbols.LibraryMusic, stringResource(R.string.library_no_tabs))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            LibraryTabs(
                                tabs = tabs,
                                selectedIndex = pagerState.currentPage.coerceIn(0, tabs.size - 1),
                                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            )
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { page ->
                                when (tabs[page]) {
                                    NavItem.ALBUMS -> AlbumsGrid(
                                        albums = albums,
                                        columns = albumColumns,
                                        cornerDp = albumCornerDp,
                                        sort = albumSort,
                                        gridState = albumsGridState,
                                        onSortSelect = viewModel::selectAlbumSort,
                                        onClick = onAlbumClick,
                                        coverArtVersion = coverArtVersion,
                                        cascade = !cascadeDone,
                                    )
                                    NavItem.ARTISTS -> PerformersList(
                                        performers = performers,
                                        sort = artistSort,
                                        listState = performersListState,
                                        onSortSelect = viewModel::selectArtistSort,
                                        onClick = onPerformerClick,
                                    )
                                    NavItem.GENRES -> GenresGrid(
                                        genres = genres,
                                        sort = genreSort,
                                        gridState = genresGridState,
                                        onSortSelect = viewModel::selectGenreSort,
                                        onClick = onGenreClick,
                                    )
                                    NavItem.PLAYLISTS -> PlaylistsCategory(
                                        viewModel = viewModel,
                                        listState = playlistsListState,
                                        onSystemClick = onPlaylistClick,
                                        onUserClick = onUserPlaylistClick,
                                    )
                                    NavItem.FAVOURITES -> EmbeddedSystemPlaylist(SystemPlaylist.FAVORITES)
                                    NavItem.ALL_SONGS -> EmbeddedSystemPlaylist(SystemPlaylist.ALL_SONGS)
                                    else -> Unit
                                }
                            }
                        }
                    }
                } else {
                    SearchResultsList(
                        results = searchResults,
                        selectedSongIds = selectedSongIds,
                        favoriteIds = favoriteIds,
                        onSongClick = viewModel::onSongClick,
                        onSongLongClick = viewModel::onSongLongClick,
                        onAlbumClick = onAlbumClick,
                        onPerformerClick = onPerformerClick,
                        onPlaylistClick = onPlaylistClick,
                        onUserPlaylistClick = onUserPlaylistClick,
                        query = query,
                        coverArtVersion = coverArtVersion,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Selection chrome covers the header while songs are picked for a bulk action.
        val selectedSongs = searchResults.songs.filter { it.id in selectedSongIds }
        SelectionChrome(
            visible = selectedSongIds.isNotEmpty(),
            selectedCount = selectedSongIds.size,
            allSelectedFavorite = selectedSongIds.isNotEmpty() && selectedSongIds.all { it in favoriteIds },
            singleSelectionHasArt = selectedSongs.size == 1 && selectedSongs.first().albumArtUri != null,
            showRemoveFromPlaylist = false,
            onClose = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onToggleFavorites = viewModel::toggleFavoritesForSelection,
            onAddToQueue = viewModel::addSelectionToQueue,
            onAddToPlaylist = viewModel::startAddToPlaylist,
            onEditTags = viewModel::startEditTags,
            onDecompose = viewModel::startDecompose,
            onChangeCoverArt = { imagePicker.launch(arrayOf("image/png", "image/jpeg")) },
            onRemoveFromPlaylist = {},
            onShare = {
                shareSongs(context, selectedSongs)
                viewModel.clearSelection()
            },
            onShowInfo = viewModel::startFileInfo,
            // The library sits below the status bar already.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (val current = songDialog) {
        is LibrarySongDialog.EditTags -> EditTagsDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onSave = { edit -> viewModel.saveTags(current.songs, edit) },
        )

        is LibrarySongDialog.Decompose -> DecomposeTitleDialog(
            songs = current.songs,
            onDismiss = viewModel::dismissDialog,
            onApply = { options -> viewModel.applyDecomposition(current.songs, options) },
        )

        is LibrarySongDialog.AddToPlaylist -> AddToPlaylistDialog(
            playlists = userPlaylists,
            onDismiss = viewModel::dismissDialog,
            onSelect = { id -> viewModel.addToExistingPlaylist(id, current.songs) },
            onCreateNew = { name -> viewModel.createPlaylistAndAdd(name, current.songs) },
        )

        is LibrarySongDialog.FileInfo -> FileInfoDialog(
            infos = current.infos,
            onDismiss = viewModel::dismissDialog,
        )

        null -> Unit
    }
}

@Composable
private fun librarySubtitle(albumCount: Int, artistCount: Int): String =
    pluralStringResource(R.plurals.album_count, albumCount, albumCount) + "  ·  " +
        pluralStringResource(R.plurals.artist_count, artistCount, artistCount)

/**
 * The top of the Library: a big rounded title with a search button that, when tapped, morphs the
 * whole header into a search field (and back).
 */
@Composable
private fun LibraryHeader(
    searching: Boolean,
    query: String,
    subtitle: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = searching,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
        },
        label = "libraryHeader",
    ) { inSearch ->
        if (inSearch) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (query.isEmpty()) focusRequester.requestFocus() }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onCloseSearch, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                            AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .focusRequester(focusRequester),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xl, end = Spacing.l, top = Spacing.l, bottom = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_title),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onOpenSearch,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                ) {
                    AppIcon(AppSymbols.Search, contentDescription = stringResource(R.string.search_hint))
                }
            }
        }
    }
}

private fun NavItem.tabSymbol(): AppSymbol = navItemUi(this).icon

/**
 * The library's sections as a scrolling row of pill toggles with icons. The selected pill fills
 * and keeps itself in view as the pager moves.
 */
@Composable
private fun LibraryTabs(
    tabs: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val rowState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        val visible = rowState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any {
            it.index == selectedIndex && it.offset >= 0 &&
                it.offset + it.size <= rowState.layoutInfo.viewportEndOffset
        }
        if (!fullyVisible) rowState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.name }) { index, tab ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(index) },
            ) {
                AnimatedAppIcon(tab.tabSymbol(), contentDescription = null, filled = checked, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(navItemUi(tab).labelRes), maxLines = 1)
            }
        }
    }
}

/**
 * The Playlists category (user + system playlists) with its create/rename/delete
 * dialogs, shared by the Library tab and the standalone Playlists screen.
 */
@Composable
private fun PlaylistsCategory(
    viewModel: LibraryViewModel,
    listState: LazyListState,
    onSystemClick: (SystemPlaylist) -> Unit,
    onUserClick: (String) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistSort by viewModel.playlistSort.collectAsStateWithLifecycle()
    val allSongsName = stringResource(R.string.playlist_all_songs)
    val favoritesName = stringResource(R.string.playlist_favorites)
    val names = mapOf(
        SystemPlaylist.ALL_SONGS to allSongsName,
        SystemPlaylist.FAVORITES to favoritesName,
    )
    LaunchedEffect(allSongsName, favoritesName) {
        viewModel.setSystemPlaylistNames(names)
    }

    var creatingPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserPlaylist?>(null) }
    var deleting by remember { mutableStateOf<UserPlaylist?>(null) }

    PlaylistsList(
        playlists = playlists,
        sort = playlistSort,
        listState = listState,
        onSortSelect = { viewModel.selectPlaylistSort(it, names) },
        onSystemClick = onSystemClick,
        onUserClick = onUserClick,
        onCreate = { creatingPlaylist = true },
        onRename = { renaming = it },
        onDelete = { deleting = it },
    )

    if (creatingPlaylist) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_new,
            confirmRes = R.string.playlist_create,
            initialName = "",
            onDismiss = { creatingPlaylist = false },
            onConfirm = {
                viewModel.createPlaylist(it)
                creatingPlaylist = false
            },
        )
    }

    renaming?.let { playlist ->
        PlaylistNameDialog(
            titleRes = R.string.playlist_rename,
            confirmRes = R.string.playlist_rename_confirm,
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renamePlaylist(playlist.id, it)
                renaming = null
            },
        )
    }

    deleting?.let { playlist ->
        FormSheet(
            title = stringResource(R.string.playlist_delete_title),
            symbol = AppSymbols.Delete,
            supportingText = stringResource(R.string.playlist_delete_message, playlist.name),
            onDismiss = { deleting = null },
            dismissLabel = stringResource(R.string.edit_tags_cancel),
            confirmLabel = stringResource(R.string.playlist_delete),
            destructive = true,
            onConfirm = {
                viewModel.deletePlaylist(playlist.id)
                deleting = null
            },
        )
    }
}

/**
 * A system playlist (Favourites / All songs) embedded as a Library tab: reuses the
 * full song-list screen with its back button hidden, scoped to its own ViewModel.
 */
@Composable
private fun EmbeddedSystemPlaylist(playlist: SystemPlaylist) {
    SystemPlaylistSongListScreen(
        playlist = playlist,
        onBack = {},
        onAlbumClick = {},
        embedded = true,
    )
}

/**
 * Standalone screen for a single library category (Albums / Artists / Genres /
 * Playlists), reached from a bottom-bar or More shortcut.
 */
@Composable
fun LibraryCategoryScreen(
    category: NavItem,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Hidden when the screen is a bottom-bar destination (a tab has nothing to go back to).
    showBack: Boolean = true,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    CollapsingScaffold(
        title = stringResource(navItemUi(category).labelRes),
        large = !showBack,
        navigationIcon = { if (showBack) BackButton(onBack) },
        modifier = modifier,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (category) {
                NavItem.ALBUMS -> {
                    val albums by viewModel.albums.collectAsStateWithLifecycle()
                    val albumColumns by viewModel.albumColumns.collectAsStateWithLifecycle()
                    val albumCornerDp by viewModel.albumCornerDp.collectAsStateWithLifecycle()
                    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
                    val albumCoverArtVersion by viewModel.coverArtVersion.collectAsStateWithLifecycle()
                    AlbumsGrid(
                        albums = albums,
                        columns = albumColumns,
                        cornerDp = albumCornerDp,
                        sort = albumSort,
                        gridState = rememberLazyGridState(),
                        onSortSelect = viewModel::selectAlbumSort,
                        onClick = onAlbumClick,
                        coverArtVersion = albumCoverArtVersion,
                    )
                }
                NavItem.ARTISTS -> {
                    val performers by viewModel.performers.collectAsStateWithLifecycle()
                    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
                    PerformersList(
                        performers = performers,
                        sort = artistSort,
                        listState = rememberLazyListState(),
                        onSortSelect = viewModel::selectArtistSort,
                        onClick = onPerformerClick,
                    )
                }
                NavItem.GENRES -> {
                    val genres by viewModel.genres.collectAsStateWithLifecycle()
                    val genreSort by viewModel.genreSort.collectAsStateWithLifecycle()
                    GenresGrid(
                        genres = genres,
                        sort = genreSort,
                        gridState = rememberLazyGridState(),
                        onSortSelect = viewModel::selectGenreSort,
                        onClick = onGenreClick,
                    )
                }
                NavItem.PLAYLISTS -> PlaylistsCategory(
                    viewModel = viewModel,
                    listState = rememberLazyListState(),
                    onSystemClick = onPlaylistClick,
                    onUserClick = onUserPlaylistClick,
                )
                else -> Unit
            }
        }
    }
}

private const val PAGE_SIZE = 4

@Composable
private fun SearchResultsList(
    results: SearchResults,
    selectedSongIds: Set<Long>,
    favoriteIds: Set<Long>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPerformerClick: (String) -> Unit,
    onPlaylistClick: (SystemPlaylist) -> Unit,
    onUserPlaylistClick: (String) -> Unit,
    query: String,
    coverArtVersion: Int,
    modifier: Modifier = Modifier,
) {
    val selectionMode = selectedSongIds.isNotEmpty()
    var songsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var albumsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var artistsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }
    var playlistsVisible by rememberSaveable(query) { mutableIntStateOf(PAGE_SIZE) }

    if (results.isEmpty) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(AppSymbols.Search, stringResource(R.string.search_no_results), polygon = MaterialShapes.Sunny)
        }
        return
    }

    val tile = MaterialTheme.colorScheme.surfaceContainerLow
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
    ) {
        if (results.songs.isNotEmpty()) {
            item(key = "header_songs") { SectionHeader(stringResource(R.string.search_section_songs)) }
            val shown = results.songs.take(songsVisible)
            itemsIndexed(shown, key = { _, song -> "song_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    leading = SongLeading.Art(song.coverArtModel(coverArtVersion)),
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song.id) },
                    selectionMode = selectionMode,
                    selected = song.id in selectedSongIds,
                    favorite = song.id in favoriteIds,
                    shape = segmentedItemShape(index, shown.size),
                    restingColor = tile,
                    modifier = Modifier.animateItem(),
                )
            }
            if (results.songs.size > songsVisible) {
                item(key = "more_songs") { ViewMoreButton { songsVisible += PAGE_SIZE } }
            }
        }

        if (results.albums.isNotEmpty()) {
            item(key = "header_albums") { SectionHeader(stringResource(R.string.tab_albums)) }
            val shown = results.albums.take(albumsVisible)
            itemsIndexed(shown, key = { _, album -> "album_${album.id}" }) { index, album ->
                ResultTile(
                    title = album.title.ifBlank { stringResource(R.string.unknown_album) },
                    subtitle = album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    shape = segmentedItemShape(index, shown.size),
                    onClick = { onAlbumClick(album.id) },
                    leading = {
                        if (album.isNoAlbum) {
                            NoAlbumArt(shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp))
                        } else {
                            AlbumArt(
                                model = album.coverArtModel(coverArtVersion),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            if (results.albums.size > albumsVisible) {
                item(key = "more_albums") { ViewMoreButton { albumsVisible += PAGE_SIZE } }
            }
        }

        if (results.artists.isNotEmpty()) {
            item(key = "header_artists") { SectionHeader(stringResource(R.string.tab_performers)) }
            val shown = results.artists.take(artistsVisible)
            itemsIndexed(shown, key = { _, artist -> "artist_${artist.name}" }) { index, artist ->
                ResultTile(
                    title = artist.name,
                    subtitle = pluralStringResource(R.plurals.song_count, artist.songCount, artist.songCount),
                    shape = segmentedItemShape(index, shown.size),
                    onClick = { onPerformerClick(artist.name) },
                    leading = { ArtistAvatar(artist.name) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (results.artists.size > artistsVisible) {
                item(key = "more_artists") { ViewMoreButton { artistsVisible += PAGE_SIZE } }
            }
        }

        if (results.playlists.isNotEmpty()) {
            item(key = "header_playlists") { SectionHeader(stringResource(R.string.tab_playlists)) }
            val shown = results.playlists.take(playlistsVisible)
            itemsIndexed(
                shown,
                key = { _, playlist -> "playlist_${playlist.systemType?.name ?: playlist.userPlaylist!!.id}" },
            ) { index, playlist ->
                val userPlaylist = playlist.userPlaylist
                ResultTile(
                    title = userPlaylist?.name ?: stringResource(systemPlaylistNameRes(playlist.systemType!!)),
                    subtitle = pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount),
                    shape = segmentedItemShape(index, shown.size),
                    onClick = {
                        if (userPlaylist != null) onUserPlaylistClick(userPlaylist.id)
                        else onPlaylistClick(playlist.systemType!!)
                    },
                    leading = { PlaylistIcon(playlist) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (results.playlists.size > playlistsVisible) {
                item(key = "more_playlists") { ViewMoreButton { playlistsVisible += PAGE_SIZE } }
            }
        }
    }
}

/** A tappable tile in a segmented list: leading visual, title, subtitle, trailing content. */
@Composable
private fun ResultTile(
    title: String,
    subtitle: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.s, top = Spacing.l, bottom = Spacing.s),
    )
}

@Composable
private fun ViewMoreButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(top = Spacing.s),
    ) {
        AppIcon(AppSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(R.string.search_view_more))
    }
}

/**
 * Runs [scrollToTop] when [sort] *changes*, and deliberately not on the first
 * composition. Re-sorting should land the user at the top of the new order, but
 * opening an album and coming back also recomposes these lists from scratch — with
 * their scroll position already restored — and an unconditional effect would throw
 * that position away every time.
 */
@Composable
private fun <T> ScrollToTopWhenSortChanges(sort: T, scrollToTop: suspend () -> Unit) {
    var appliedSort by remember { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        if (sort != appliedSort) {
            appliedSort = sort
            scrollToTop()
        }
    }
}

/** The sort pill on its own line at the top of a page; scrolls away with the content. */
@Composable
private fun <T : Enum<T>> SortBar(options: List<T>, sort: SortState<T>, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        SortButton(options = options, sort = sort, onSelect = onSelect)
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    columns: Int,
    cornerDp: Int,
    sort: SortState<AlbumSortOption>,
    gridState: LazyGridState,
    onSortSelect: (AlbumSortOption) -> Unit,
    onClick: (Long) -> Unit,
    coverArtVersion: Int = 0,
    cascade: Boolean = false,
) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(AppSymbols.Album, stringResource(R.string.library_empty_albums))
        }
        return
    }
    ScrollToTopWhenSortChanges(sort) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        // Cards carry an 8dp inset of their own, so the gutters and gaps are trimmed to match.
        contentPadding = PaddingValues(start = Spacing.s, end = Spacing.s, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = Spacing.s)) { SortBar(AlbumSortOption.entries, sort, onSortSelect) }
        }
        itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
            AlbumCard(
                album = album,
                cornerDp = cornerDp,
                onClick = onClick,
                coverArtVersion = coverArtVersion,
                modifier = if (cascade && index < ENTRANCE_ITEMS) Modifier.entrance(index) else Modifier,
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    cornerDp: Int,
    onClick: (Long) -> Unit,
    coverArtVersion: Int,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val artShape = RoundedCornerShape(cornerDp.dp)
    // The rounded clip is only for the press ripple; the inner inset keeps both the cover's own
    // corners (exactly as square as the setting says) and the text clear of it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = ripple()) { onClick(album.id) }
            .padding(Spacing.s),
    ) {
        val coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .sharedElement(albumArtKey(album.id), artShape)
        if (album.isNoAlbum) {
            NoAlbumArt(shape = artShape, modifier = coverModifier)
        } else {
            AlbumArt(model = album.coverArtModel(coverArtVersion), shape = artShape, modifier = coverModifier)
        }
        Text(
            text = if (album.isNoAlbum) {
                stringResource(R.string.no_album)
            } else {
                album.title.ifBlank { stringResource(R.string.unknown_album) }
            },
            style = MaterialTheme.typography.titleSmallEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs, end = Spacing.xs),
        )
        if (!album.isNoAlbum) {
            Text(
                text = album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun PerformersList(
    performers: List<Performer>,
    sort: SortState<ArtistSortOption>,
    listState: LazyListState,
    onSortSelect: (ArtistSortOption) -> Unit,
    onClick: (String) -> Unit,
) {
    if (performers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(AppSymbols.Person, stringResource(R.string.library_empty_performers), polygon = MaterialShapes.Clover4Leaf)
        }
        return
    }
    ScrollToTopWhenSortChanges(sort) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort") { SortBar(ArtistSortOption.entries, sort, onSortSelect) }
        itemsIndexed(performers, key = { _, performer -> performer.name }) { index, performer ->
            ResultTile(
                title = performer.name,
                subtitle = pluralStringResource(R.plurals.song_count, performer.songCount, performer.songCount),
                shape = segmentedItemShape(index, performers.size),
                onClick = { onClick(performer.name) },
                leading = { ArtistAvatar(performer.name) },
                trailing = {
                    AppIcon(AppSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }
}

/** Genre shapes; a genre always gets the same one. */
private val GenrePolygons = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Flower,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Burst,
    MaterialShapes.Pill,
    MaterialShapes.Arch,
    MaterialShapes.Ghostish,
)

@Composable
private fun GenresGrid(
    genres: List<Genre>,
    sort: SortState<GenreSortOption>,
    gridState: LazyGridState,
    onSortSelect: (GenreSortOption) -> Unit,
    onClick: (String) -> Unit,
) {
    if (genres.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(AppSymbols.Category, stringResource(R.string.library_empty_genres), polygon = MaterialShapes.Burst)
        }
        return
    }
    ScrollToTopWhenSortChanges(sort) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            SortBar(GenreSortOption.entries, sort, onSortSelect)
        }
        itemsIndexed(genres, key = { _, genre -> genre.name }) { _, genre ->
            GenreTile(genre = genre, onClick = { onClick(genre.name) })
        }
    }
}

/** A tonal tile for a genre with its shape peeking out of the corner. */
@Composable
private fun GenreTile(genre: Genre, onClick: () -> Unit) {
    val (container, content) = tonalPairFor(genre.name)
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .pressScale(interaction),
    ) {
        Box(Modifier.fillMaxSize()) {
            ShapedIcon(
                symbol = AppSymbols.MusicNote,
                polygon = GenrePolygons[stableIndex(genre.name, GenrePolygons.size)],
                size = 72.dp,
                iconSize = 28.dp,
                containerColor = content.copy(alpha = 0.12f),
                contentColor = content,
                containerRotation = 15f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.s),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.l),
            ) {
                Text(
                    genre.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.song_count, genre.songCount, genre.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun PlaylistIcon(playlist: Playlist) {
    when (playlist.systemType) {
        SystemPlaylist.FAVORITES -> ShapedIcon(
            symbol = AppSymbols.Favorite,
            polygon = MaterialShapes.Heart,
            size = 48.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SystemPlaylist.ALL_SONGS -> ShapedIcon(
            symbol = AppSymbols.MusicNote,
            polygon = MaterialShapes.Cookie9Sided,
            size = 48.dp,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        null -> {
            val name = playlist.userPlaylist?.name.orEmpty()
            val (container, content) = tonalPairFor(name)
            ShapedIcon(
                symbol = AppSymbols.QueueMusic,
                polygon = MaterialShapes.Square,
                size = 48.dp,
                containerColor = container,
                contentColor = content,
            )
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    sort: SortState<PlaylistSortOption>,
    listState: LazyListState,
    onSortSelect: (PlaylistSortOption) -> Unit,
    onSystemClick: (SystemPlaylist) -> Unit,
    onUserClick: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (UserPlaylist) -> Unit,
    onDelete: (UserPlaylist) -> Unit,
) {
    ScrollToTopWhenSortChanges(sort) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortButton(PlaylistSortOption.entries, sort, onSortSelect)
                Spacer(Modifier.weight(1f))
                Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.playlist_new))
                }
            }
        }
        itemsIndexed(playlists, key = { _, playlist -> playlist.systemType?.name ?: playlist.userPlaylist!!.id }) { index, playlist ->
            val userPlaylist = playlist.userPlaylist
            ResultTile(
                title = userPlaylist?.name ?: stringResource(systemPlaylistNameRes(playlist.systemType!!)),
                subtitle = pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount) +
                    if (playlist.durationMs > 0) "  ·  " + formatDuration(playlist.durationMs) else "",
                shape = segmentedItemShape(index, playlists.size),
                onClick = {
                    if (userPlaylist != null) onUserClick(userPlaylist.id)
                    else onSystemClick(playlist.systemType!!)
                },
                leading = { PlaylistIcon(playlist) },
                trailing = userPlaylist?.let { user ->
                    {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                                AppIcon(AppSymbols.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                AppMenuGroup {
                                    AppMenuItem(
                                        text = stringResource(R.string.playlist_rename),
                                        symbol = AppSymbols.Edit,
                                        onClick = {
                                            menuOpen = false
                                            onRename(user)
                                        },
                                    )
                                    AppMenuItem(
                                        text = stringResource(R.string.playlist_delete),
                                        symbol = AppSymbols.Delete,
                                        destructive = true,
                                        onClick = {
                                            menuOpen = false
                                            onDelete(user)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
