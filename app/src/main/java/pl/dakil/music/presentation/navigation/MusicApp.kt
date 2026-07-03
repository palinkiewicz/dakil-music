package pl.dakil.music.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.domain.model.SystemPlaylist
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.backup.BackupScreen
import pl.dakil.music.presentation.history.ListeningHistoryScreen
import pl.dakil.music.presentation.library.LibraryCategoryScreen
import pl.dakil.music.presentation.library.LibraryScreen
import pl.dakil.music.presentation.lyrics.LyricsScreen
import pl.dakil.music.presentation.more.AboutDialog
import pl.dakil.music.presentation.more.MoreScreen
import pl.dakil.music.presentation.more.MoreViewModel
import pl.dakil.music.presentation.statistics.StatisticsScreen
import pl.dakil.music.presentation.nowplaying.NowPlayingNavIcon
import pl.dakil.music.presentation.nowplaying.NowPlayingScreen
import pl.dakil.music.presentation.nowplaying.NowPlayingViewModel
import pl.dakil.music.presentation.settings.AlbumRulesScreen
import pl.dakil.music.presentation.settings.NavigationCustomizationScreen
import pl.dakil.music.presentation.settings.NavigationCustomizationViewModel
import pl.dakil.music.presentation.settings.SettingsScreen
import pl.dakil.music.presentation.songlist.SongListScreen
import pl.dakil.music.presentation.songlist.SystemPlaylistSongListScreen

/** Maps the current route to the bottom-bar item that should look selected. */
private fun activeNavItem(currentRoute: String?, bottomItems: List<NavItem>): NavItem? {
    fun routeOf(item: NavItem) = (navItemUi(item).action as? NavAction.Route)?.route
    bottomItems.firstOrNull { routeOf(it) == currentRoute }?.let { return it }

    val parentRoute = when {
        currentRoute == Routes.LYRICS -> Routes.NOW_PLAYING
        currentRoute?.startsWith(Routes.SONG_LIST) == true -> Routes.LIBRARY
        currentRoute in setOf(
            Routes.SETTINGS, Routes.ALBUM_RULES, Routes.NAVIGATION,
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

    // A notification tap requests the Now Playing tab; navigate like a bottom-bar switch.
    LaunchedEffect(navigateToNowPlaying) {
        navigateToNowPlaying.collect { navigateTopLevel(Routes.NOW_PLAYING) }
    }

    // Shared playback state drives the live Now Playing tab icon.
    val nowPlayingViewModel: NowPlayingViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val nowPlaying by nowPlayingViewModel.uiState.collectAsStateWithLifecycle()
    val progress = if (nowPlaying.durationMs > 0L) {
        nowPlaying.positionMs.toFloat() / nowPlaying.durationMs
    } else {
        0f
    }

    // Bottom-bar layout + refresh/about actions come from the navigation customization.
    val navViewModel: NavigationCustomizationViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val navConfig by navViewModel.config.collectAsStateWithLifecycle()
    val bottomItems = navConfig.enabled(NavComponent.BOTTOM_BAR)
    val moreViewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory)
    var showAbout by remember { mutableStateOf(false) }
    val activeItem = activeNavItem(currentRoute, bottomItems)

    fun onNavItem(item: NavItem) {
        when (val action = navItemUi(item).action) {
            is NavAction.Route -> navigateTopLevel(action.route)
            NavAction.Refresh -> moreViewModel.refreshLibrary()
            NavAction.About -> showAbout = true
        }
    }

    Scaffold(
        // Insets handled per-screen to avoid double padding with inner Scaffolds/TopAppBars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Always visible so the user can see playback has started from any screen.
            NavigationBar {
                bottomItems.forEach { item ->
                    val ui = navItemUi(item)
                    val route = (ui.action as? NavAction.Route)?.route
                    NavigationBarItem(
                        selected = item == activeItem,
                        onClick = {
                            if (item == activeItem && route != null) {
                                // Re-tapping the active tab: pop to it, or let it reset.
                                if (currentRoute != route) {
                                    navController.popBackStack(route, inclusive = false)
                                } else {
                                    reselectFlow.tryEmit(route)
                                }
                            } else {
                                onNavItem(item)
                            }
                        },
                        icon = {
                            if (item == NavItem.NOW_PLAYING) {
                                NowPlayingNavIcon(
                                    albumArtUri = nowPlaying.song?.albumArtUri,
                                    hasSong = nowPlaying.song != null,
                                    progress = progress,
                                )
                            } else {
                                Icon(ui.icon, contentDescription = null)
                            }
                        },
                        label = {
                            Text(
                                stringResource(ui.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    modifier = Modifier.statusBarsPadding(),
                    onReselect = remember { reselectFlow.filter { it == Routes.NOW_PLAYING }.map {} },
                    onOpenLyrics = { navController.navigate(Routes.LYRICS) },
                )
            }
            composable(Routes.LIBRARY) {
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
            composable(Routes.MORE) {
                MoreScreen(
                    onOpen = { item ->
                        val route = (navItemUi(item).action as? NavAction.Route)?.route ?: return@MoreScreen
                        if (item == NavItem.NOW_PLAYING || item == NavItem.LIBRARY) {
                            navigateTopLevel(route)
                        } else {
                            navController.navigate(route)
                        }
                    },
                    onReselect = remember { reselectFlow.filter { it == Routes.MORE }.map {} },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbumRules = { navController.navigate(Routes.ALBUM_RULES) },
                    onOpenNavigation = { navController.navigate(Routes.NAVIGATION) },
                )
            }
            composable(Routes.ALBUM_RULES) {
                AlbumRulesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NAVIGATION) {
                NavigationCustomizationScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LISTENING_HISTORY) {
                ListeningHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LYRICS) {
                LyricsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SONG_LIST_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_SOURCE_ARG) { type = NavType.StringType },
                ),
            ) {
                SongListScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                )
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
