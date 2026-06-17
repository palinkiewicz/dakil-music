package pl.dakil.music.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.dakil.music.R
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.history.ListeningHistoryScreen
import pl.dakil.music.presentation.library.LibraryScreen
import pl.dakil.music.presentation.more.MoreScreen
import pl.dakil.music.presentation.statistics.StatisticsScreen
import pl.dakil.music.presentation.nowplaying.NowPlayingNavIcon
import pl.dakil.music.presentation.nowplaying.NowPlayingScreen
import pl.dakil.music.presentation.nowplaying.NowPlayingViewModel
import pl.dakil.music.presentation.settings.AlbumRulesScreen
import pl.dakil.music.presentation.settings.SettingsScreen
import pl.dakil.music.presentation.songlist.SongListScreen

private enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    NOW_PLAYING(Routes.NOW_PLAYING, Icons.Rounded.PlayCircle, R.string.nav_now_playing),
    LIBRARY(Routes.LIBRARY, Icons.Rounded.LibraryMusic, R.string.nav_library),
    MORE(Routes.MORE, Icons.Rounded.MoreHoriz, R.string.nav_more),
}

@Composable
fun MusicApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Detail screens map back to their parent tab so a tab always looks selected.
    val activeRoute = when (currentRoute) {
        Routes.SETTINGS, Routes.ALBUM_RULES, Routes.LISTENING_HISTORY, Routes.STATISTICS -> Routes.MORE
        else -> if (currentRoute?.startsWith(Routes.SONG_LIST) == true) Routes.LIBRARY else currentRoute
    }

    // Emits the route of a tab that was tapped while already on its main screen, so that
    // screen can react (scroll to top / exit search). Subscreen reselects pop instead.
    val reselectFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    // Shared playback state drives the live Now Playing tab icon.
    val nowPlayingViewModel: NowPlayingViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val nowPlaying by nowPlayingViewModel.uiState.collectAsStateWithLifecycle()
    val progress = if (nowPlaying.durationMs > 0L) {
        nowPlaying.positionMs.toFloat() / nowPlaying.durationMs
    } else {
        0f
    }

    Scaffold(
        // Insets handled per-screen to avoid double padding with inner Scaffolds/TopAppBars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Always visible so the user can see playback has started from any screen.
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.route == activeRoute,
                        onClick = {
                            if (destination.route == activeRoute) {
                                // Re-tapping the active tab.
                                if (currentRoute != destination.route) {
                                    // Inside a subscreen → return to the tab's main screen.
                                    navController.popBackStack(destination.route, inclusive = false)
                                } else {
                                    // Already on the main screen → let it reset (scroll/search).
                                    reselectFlow.tryEmit(destination.route)
                                }
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (destination == TopLevelDestination.NOW_PLAYING) {
                                NowPlayingNavIcon(
                                    albumArtUri = nowPlaying.song?.albumArtUri,
                                    hasSong = nowPlaying.song != null,
                                    progress = progress,
                                )
                            } else {
                                Icon(destination.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
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
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    modifier = Modifier.statusBarsPadding(),
                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                    onPerformerClick = { navController.navigate(Routes.performerSongs(it)) },
                    onPlaylistClick = { navController.navigate(Routes.playlistSongs(it)) },
                    onUserPlaylistClick = { navController.navigate(Routes.userPlaylistSongs(it)) },
                    onReselect = remember { reselectFlow.filter { it == Routes.LIBRARY }.map {} },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenListeningHistory = { navController.navigate(Routes.LISTENING_HISTORY) },
                    onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                    onReselect = remember { reselectFlow.filter { it == Routes.MORE }.map {} },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbumRules = { navController.navigate(Routes.ALBUM_RULES) },
                )
            }
            composable(Routes.ALBUM_RULES) {
                AlbumRulesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LISTENING_HISTORY) {
                ListeningHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen(onBack = { navController.popBackStack() })
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
        }
    }
}
