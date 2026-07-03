package pl.dakil.music.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem

/** What tapping a [NavItem] does: open a route, or perform an in-place action. */
sealed interface NavAction {
    data class Route(val route: String) : NavAction
    data object Refresh : NavAction
    data object About : NavAction
}

/** Presentation metadata (icon, label, summary, behaviour) for each [NavItem]. */
data class NavItemUi(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    @param:StringRes val summaryRes: Int,
    val action: NavAction,
)

fun navItemUi(item: NavItem): NavItemUi = when (item) {
    NavItem.NOW_PLAYING -> NavItemUi(
        Icons.Rounded.PlayCircle, R.string.nav_now_playing, R.string.nav_now_playing_summary,
        NavAction.Route(Routes.NOW_PLAYING),
    )
    NavItem.LIBRARY -> NavItemUi(
        Icons.Rounded.LibraryMusic, R.string.nav_library, R.string.nav_library_summary,
        NavAction.Route(Routes.LIBRARY),
    )
    NavItem.LISTENING_HISTORY -> NavItemUi(
        Icons.Rounded.History, R.string.more_listening_history, R.string.more_listening_history_summary,
        NavAction.Route(Routes.LISTENING_HISTORY),
    )
    NavItem.STATISTICS -> NavItemUi(
        Icons.Rounded.BarChart, R.string.more_statistics, R.string.more_statistics_summary,
        NavAction.Route(Routes.STATISTICS),
    )
    NavItem.REFRESH_LIBRARY -> NavItemUi(
        Icons.Rounded.Refresh, R.string.more_refresh, R.string.more_refresh_summary,
        NavAction.Refresh,
    )
    NavItem.SETTINGS -> NavItemUi(
        Icons.Rounded.Settings, R.string.more_settings, R.string.more_settings_summary,
        NavAction.Route(Routes.SETTINGS),
    )
    NavItem.BACKUP -> NavItemUi(
        Icons.Rounded.Inventory2, R.string.more_backup, R.string.more_backup_summary,
        NavAction.Route(Routes.BACKUP),
    )
    NavItem.ABOUT -> NavItemUi(
        Icons.Rounded.Info, R.string.more_about, R.string.more_about_summary,
        NavAction.About,
    )
    NavItem.ALBUMS -> NavItemUi(
        Icons.Rounded.Album, R.string.tab_albums, R.string.nav_albums_summary,
        NavAction.Route(Routes.ALBUMS),
    )
    NavItem.ARTISTS -> NavItemUi(
        Icons.Rounded.Person, R.string.tab_performers, R.string.nav_artists_summary,
        NavAction.Route(Routes.ARTISTS),
    )
    NavItem.GENRES -> NavItemUi(
        Icons.Rounded.Category, R.string.tab_genres, R.string.nav_genres_summary,
        NavAction.Route(Routes.GENRES),
    )
    NavItem.PLAYLISTS -> NavItemUi(
        Icons.Rounded.QueueMusic, R.string.tab_playlists, R.string.nav_playlists_summary,
        NavAction.Route(Routes.PLAYLISTS),
    )
    NavItem.FAVOURITES -> NavItemUi(
        Icons.Rounded.Favorite, R.string.playlist_favorites, R.string.nav_favourites_summary,
        NavAction.Route(Routes.FAVOURITES),
    )
    NavItem.ALL_SONGS -> NavItemUi(
        Icons.Rounded.MusicNote, R.string.playlist_all_songs, R.string.nav_all_songs_summary,
        NavAction.Route(Routes.ALL_SONGS),
    )
    NavItem.MORE -> NavItemUi(
        Icons.Rounded.MoreHoriz, R.string.nav_more, R.string.nav_more_summary,
        NavAction.Route(Routes.MORE),
    )
}
