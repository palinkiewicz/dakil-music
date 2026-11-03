package pl.dakil.music.presentation.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.backup.BackupScreen
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.history.ListeningHistoryScreen
import pl.dakil.music.presentation.library.LibraryCategoryScreen
import pl.dakil.music.presentation.library.LibraryScreen
import pl.dakil.music.presentation.more.AboutDialog
import pl.dakil.music.presentation.more.MoreScreen
import pl.dakil.music.presentation.more.MoreViewModel
import pl.dakil.music.presentation.nowplaying.MiniPlayerDockHeight
import pl.dakil.music.presentation.nowplaying.NowPlayingNavIcon
import pl.dakil.music.presentation.nowplaying.NowPlayingViewModel
import pl.dakil.music.presentation.nowplaying.PlayerHost
import pl.dakil.music.presentation.nowplaying.rememberPlayerSheetState
import pl.dakil.music.presentation.settings.AlbumRulesScreen
import pl.dakil.music.presentation.settings.NavigationCustomizationScreen
import pl.dakil.music.presentation.settings.NavigationCustomizationViewModel
import pl.dakil.music.presentation.settings.AppearanceSettingsScreen
import pl.dakil.music.presentation.settings.LibrarySettingsScreen
import pl.dakil.music.presentation.settings.PlaybackSettingsScreen
import pl.dakil.music.presentation.settings.SettingsScreen
import pl.dakil.music.presentation.settings.StatisticsSettingsScreen
import pl.dakil.music.presentation.songlist.SongListScreen
import pl.dakil.music.presentation.songlist.SystemPlaylistSongListScreen
import pl.dakil.music.presentation.statistics.StatisticsScreen
import pl.dakil.music.presentation.components.LocalIsPlaying
import pl.dakil.music.ui.designsystem.LocalNavAnimatedScope
import pl.dakil.music.ui.designsystem.LocalSharedTransitionScope
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.rememberHaptics

/** Exposes the destination's transition scope to shared elements inside it. */
@Composable
private fun AnimatedContentScope.Destination(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)
}

private fun routeOf(item: NavItem) = (navItemUi(item).action as? NavAction.Route)?.route

/** Maps the current route to the bottom-bar item that should look selected. */
private fun activeNavItem(currentRoute: String?, bottomItems: List<NavItem>): NavItem? {
    bottomItems.firstOrNull { routeOf(it) == currentRoute }?.let { return it }

    val parentRoute = when {
        currentRoute?.startsWith(Routes.SONG_LIST) == true -> Routes.LIBRARY
        currentRoute in setOf(
            Routes.SETTINGS, Routes.ALBUM_RULES, Routes.NAVIGATION,
            Routes.SETTINGS_APPEARANCE, Routes.SETTINGS_LIBRARY, Routes.SETTINGS_PLAYBACK, Routes.SETTINGS_STATISTICS,
            Routes.LISTENING_HISTORY, Routes.STATISTICS, Routes.BACKUP,
        ) -> Routes.MORE
        else -> currentRoute
    }
    return bottomItems.firstOrNull { routeOf(it) == parentRoute }
}

@Composable
fun MusicApp(navigateToNowPlaying: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    // Top-level (tab) navigation: switch tab, restoring the destination's saved state.
    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Emits the route of a tab that was tapped while already on its main screen, so that
    // screen can react (scroll to top / exit search). Subscreen reselects pop instead.
    val reselectFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    // The player lives above every screen; "Now Playing" (nav item, notification, voice
    // search) opens it rather than navigating.
    val playerSheet = rememberPlayerSheetState()
    LaunchedEffect(navigateToNowPlaying) {
        navigateToNowPlaying.collect { scope.launch { playerSheet.expand() } }
    }

    // Shared playback state drives the dock and the live Now Playing nav icon.
    val nowPlayingViewModel: NowPlayingViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val nowPlaying by nowPlayingViewModel.uiState.collectAsStateWithLifecycle()
    val hasSong = nowPlaying.song != null
    val progress = if (nowPlaying.durationMs > 0L) nowPlaying.positionMs.toFloat() / nowPlaying.durationMs else 0f

    // Bottom-bar layout + refresh/about actions come from the navigation customization.
    val navViewModel: NavigationCustomizationViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val navConfig by navViewModel.config.collectAsStateWithLifecycle()
    val bottomItems = navConfig.enabled(NavComponent.BOTTOM_BAR)
    val tabRoutes = remember(bottomItems) { bottomItems.mapNotNull(::routeOf).toSet() + Routes.LIBRARY }
    val moreViewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory)
    var showAbout by remember { mutableStateOf(false) }
    val activeItem = activeNavItem(currentRoute, bottomItems)

    fun onNavItem(item: NavItem) {
        if (item == NavItem.NOW_PLAYING) {
            if (hasSong) scope.launch { playerSheet.expand() }
            return
        }
        when (val action = navItemUi(item).action) {
            is NavAction.Route -> navigateTopLevel(action.route)
            NavAction.Refresh -> moreViewModel.refreshLibrary()
            NavAction.About -> showAbout = true
        }
    }

    fun NavBackStackEntry.isTabRoot() = destination.route in tabRoutes

    // A bottom-bar tab lands directly on top of the start destination, so it has no parent to go
    // back to; the same screen reached from More (or another screen) sits above it.
    @Composable
    fun rememberShowBack(entry: NavBackStackEntry): Boolean = remember(entry) {
        navController.previousBackStackEntry?.destination?.route !=
            navController.graph.findStartDestination().route
    }

    // The dock under the player: the navigation bar, plus room for the mini player while a
    // song is loaded. It sinks out of the way as the player opens.
    var navBarHeight by remember { mutableStateOf(0.dp) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Scaffold(
            // Insets handled per-screen to avoid double padding with inner Scaffolds/TopAppBars.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = size.height * playerSheet.fraction }
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    AnimatedVisibility(
                        visible = hasSong && nowPlaying.miniPlayerEnabled,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                    ) {
                        Spacer(Modifier.height(MiniPlayerDockHeight))
                    }
                    ShortNavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
                    ) {
                        bottomItems.forEach { item ->
                            val ui = navItemUi(item)
                            val route = (ui.action as? NavAction.Route)?.route
                            val selected = item == activeItem
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected && route != null) {
                                        // Re-tapping the active tab: pop to it, or let it reset.
                                        if (currentRoute != route) {
                                            navController.popBackStack(route, inclusive = false)
                                        } else {
                                            reselectFlow.tryEmit(route)
                                        }
                                    } else {
                                        haptics.tick()
                                        onNavItem(item)
                                    }
                                },
                                icon = {
                                    if (item == NavItem.NOW_PLAYING) {
                                        NowPlayingNavIcon(
                                            artModel = nowPlaying.song?.coverArtModel(),
                                            hasSong = hasSong,
                                            progress = progress,
                                            isPlaying = nowPlaying.isPlaying,
                                            selected = selected,
                                        )
                                    } else {
                                        AnimatedAppIcon(ui.icon, contentDescription = null, filled = selected)
                                    }
                                },
                                label = {
                                    Text(stringResource(ui.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                    LocalIsPlaying provides nowPlaying.isPlaying,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.LIBRARY,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            // Paint the screen background so nothing flashes through the cross-fades.
                            .background(MaterialTheme.colorScheme.surface),
                        // Switching between bottom-bar tabs plays Material fade-through (no direction);
                        // pushed destinations play the spring-driven Material 3 shared-axis X.
                        enterTransition = {
                            if (initialState.isTabRoot() && targetState.isTabRoot()) NavTransitions.fadeThroughEnter()
                            else NavTransitions.pushEnter()
                        },
                        exitTransition = {
                            if (initialState.isTabRoot() && targetState.isTabRoot()) NavTransitions.fadeThroughExit()
                            else NavTransitions.pushExit()
                        },
                        popEnterTransition = {
                            if (initialState.isTabRoot() && targetState.isTabRoot()) NavTransitions.fadeThroughEnter()
                            else NavTransitions.popEnter()
                        },
                        popExitTransition = {
                            if (initialState.isTabRoot() && targetState.isTabRoot()) NavTransitions.fadeThroughExit()
                            else NavTransitions.popExit()
                        },
                    ) {
                        composable(Routes.LIBRARY) {
                            Destination {
                                LibraryScreen(
                                    modifier = Modifier.statusBarsPadding(),
                                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                                    onPerformerClick = { navController.navigate(Routes.performerSongs(it)) },
                                    onGenreClick = { navController.navigate(Routes.genreSongs(it)) },
                                    onPlaylistClick = { navController.navigate(Routes.playlistSongs(it)) },
                                    onUserPlaylistClick = { navController.navigate(Routes.userPlaylistSongs(it)) },
                                    onReselect = remember { reselectFlow.filter { it == Routes.LIBRARY }.map {} },
                                )
                            }
                        }
                        composable(Routes.MORE) {
                            MoreScreen(
                                onOpen = { item ->
                                    if (item == NavItem.NOW_PLAYING) {
                                        onNavItem(item)
                                        return@MoreScreen
                                    }
                                    val route = routeOf(item) ?: return@MoreScreen
                                    if (item == NavItem.LIBRARY) navigateTopLevel(route) else navController.navigate(route)
                                },
                                onReselect = remember { reselectFlow.filter { it == Routes.MORE }.map {} },
                            )
                        }
                        composable(Routes.SETTINGS) { entry ->
                            SettingsScreen(
                                showBack = rememberShowBack(entry),
                                onBack = { navController.popBackStack() },
                                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                                onOpenLibrary = { navController.navigate(Routes.SETTINGS_LIBRARY) },
                                onOpenPlayback = { navController.navigate(Routes.SETTINGS_PLAYBACK) },
                                onOpenStatistics = { navController.navigate(Routes.SETTINGS_STATISTICS) },
                                onOpenNavigation = { navController.navigate(Routes.NAVIGATION) },
                            )
                        }
                        composable(Routes.SETTINGS_APPEARANCE) {
                            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS_LIBRARY) {
                            LibrarySettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenAlbumRules = { navController.navigate(Routes.ALBUM_RULES) },
                            )
                        }
                        composable(Routes.SETTINGS_PLAYBACK) {
                            PlaybackSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS_STATISTICS) {
                            StatisticsSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.ALBUM_RULES) {
                            AlbumRulesScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.NAVIGATION) {
                            NavigationCustomizationScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.LISTENING_HISTORY) { entry ->
                            ListeningHistoryScreen(onBack = { navController.popBackStack() }, showBack = rememberShowBack(entry))
                        }
                        composable(Routes.STATISTICS) { entry ->
                            StatisticsScreen(onBack = { navController.popBackStack() }, showBack = rememberShowBack(entry))
                        }
                        composable(Routes.BACKUP) { entry ->
                            BackupScreen(onBack = { navController.popBackStack() }, showBack = rememberShowBack(entry))
                        }
                        composable(
                            route = Routes.SONG_LIST_PATTERN,
                            arguments = listOf(
                                navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType },
                                navArgument(Routes.ARG_SOURCE_ARG) { type = NavType.StringType },
                            ),
                        ) {
                            Destination {
                                SongListScreen(
                                    onBack = { navController.popBackStack() },
                                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                                )
                            }
                        }
                        // Standalone library-category screens (bottom-bar / More shortcuts).
                        val categoryRoutes = mapOf(
                            Routes.ALBUMS to NavItem.ALBUMS,
                            Routes.ARTISTS to NavItem.ARTISTS,
                            Routes.GENRES to NavItem.GENRES,
                            Routes.PLAYLISTS to NavItem.PLAYLISTS,
                        )
                        categoryRoutes.forEach { (route, category) ->
                            composable(route) { entry ->
                                // A bottom-bar tab lands directly on top of the start destination, so it
                                // has no parent to go back to; reached from More it sits above More.
                                val showBack = remember(entry) {
                                    navController.previousBackStackEntry?.destination?.route !=
                                        navController.graph.findStartDestination().route
                                }
                                Destination {
                                    LibraryCategoryScreen(
                                        category = category,
                                        onBack = { navController.popBackStack() },
                                        onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                                        onPerformerClick = { navController.navigate(Routes.performerSongs(it)) },
                                        onGenreClick = { navController.navigate(Routes.genreSongs(it)) },
                                        onPlaylistClick = { navController.navigate(Routes.playlistSongs(it)) },
                                        onUserPlaylistClick = { navController.navigate(Routes.userPlaylistSongs(it)) },
                                        modifier = Modifier.statusBarsPadding(),
                                        showBack = showBack,
                                    )
                                }
                            }
                        }
                        // Dedicated Favourites / All songs shortcuts (bottom-bar / More).
                        val systemPlaylistRoutes = mapOf(
                            Routes.FAVOURITES to SystemPlaylist.FAVORITES,
                            Routes.ALL_SONGS to SystemPlaylist.ALL_SONGS,
                        )
                        systemPlaylistRoutes.forEach { (route, playlist) ->
                            composable(route) { entry ->
                                val showBack = remember(entry) {
                                    navController.previousBackStackEntry?.destination?.route !=
                                        navController.graph.findStartDestination().route
                                }
                                SystemPlaylistSongListScreen(
                                    playlist = playlist,
                                    onBack = { navController.popBackStack() },
                                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                                    showBack = showBack,
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerHost(
            sheet = playerSheet,
            viewModel = nowPlayingViewModel,
            dockBottom = navBarHeight,
            onOpenAlbum = { navController.navigate(Routes.albumSongs(it)) },
            onOpenArtist = { navController.navigate(Routes.performerSongs(it)) },
        )

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
    }
}
