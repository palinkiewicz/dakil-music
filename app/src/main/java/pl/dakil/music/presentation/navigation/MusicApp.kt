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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.dakil.music.R
import pl.dakil.music.presentation.library.LibraryScreen
import pl.dakil.music.presentation.more.MoreScreen
import pl.dakil.music.presentation.nowplaying.NowPlayingScreen
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
    val currentDestination = backStackEntry?.destination

    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()
    val showBottomBar = currentDestination?.route in topLevelRoutes

    Scaffold(
        // Insets handled per-screen to avoid double padding with inner Scaffolds/TopAppBars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
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
                NowPlayingScreen(modifier = Modifier.statusBarsPadding())
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    modifier = Modifier.statusBarsPadding(),
                    onAlbumClick = { navController.navigate(Routes.albumSongs(it)) },
                    onPerformerClick = { navController.navigate(Routes.performerSongs(it)) },
                    onPlaylistClick = { navController.navigate(Routes.playlistSongs(it)) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SONG_LIST_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_SOURCE_ARG) { type = NavType.StringType },
                ),
            ) {
                SongListScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
