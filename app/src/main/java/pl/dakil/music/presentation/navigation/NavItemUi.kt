package pl.dakil.music.presentation.navigation

import androidx.annotation.StringRes
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/** What tapping a [NavItem] does: open a route, or perform an in-place action. */
sealed interface NavAction {
    data class Route(val route: String) : NavAction
    data object Refresh : NavAction
    data object About : NavAction
}

/** Presentation metadata (icon, label, summary, behaviour) for each [NavItem]. */
data class NavItemUi(
    val icon: AppSymbol,
    @param:StringRes val labelRes: Int,
    @param:StringRes val summaryRes: Int,
    val action: NavAction,
)

fun navItemUi(item: NavItem): NavItemUi = when (item) {
    NavItem.NOW_PLAYING -> NavItemUi(
        AppSymbols.PlayCircle, R.string.nav_now_playing, R.string.nav_now_playing_summary,
        NavAction.Route(Routes.NOW_PLAYING),
    )
    NavItem.LIBRARY -> NavItemUi(
        AppSymbols.LibraryMusic, R.string.nav_library, R.string.nav_library_summary,
        NavAction.Route(Routes.LIBRARY),
    )
    NavItem.LISTENING_HISTORY -> NavItemUi(
        AppSymbols.History, R.string.more_listening_history, R.string.more_listening_history_summary,
        NavAction.Route(Routes.LISTENING_HISTORY),
    )
    NavItem.STATISTICS -> NavItemUi(
        AppSymbols.BarChart, R.string.more_statistics, R.string.more_statistics_summary,
        NavAction.Route(Routes.STATISTICS),
    )
    NavItem.REFRESH_LIBRARY -> NavItemUi(
        AppSymbols.Refresh, R.string.more_refresh, R.string.more_refresh_summary,
        NavAction.Refresh,
    )
    NavItem.SETTINGS -> NavItemUi(
        AppSymbols.Settings, R.string.more_settings, R.string.more_settings_summary,
        NavAction.Route(Routes.SETTINGS),
    )
    NavItem.BACKUP -> NavItemUi(
        AppSymbols.Inventory2, R.string.more_backup, R.string.more_backup_summary,
        NavAction.Route(Routes.BACKUP),
    )
    NavItem.ABOUT -> NavItemUi(
        AppSymbols.Info, R.string.more_about, R.string.more_about_summary,
        NavAction.About,
    )
    NavItem.ALBUMS -> NavItemUi(
        AppSymbols.Album, R.string.tab_albums, R.string.nav_albums_summary,
        NavAction.Route(Routes.ALBUMS),
    )
    NavItem.ARTISTS -> NavItemUi(
        AppSymbols.Person, R.string.tab_performers, R.string.nav_artists_summary,
        NavAction.Route(Routes.ARTISTS),
    )
    NavItem.GENRES -> NavItemUi(
        AppSymbols.Category, R.string.tab_genres, R.string.nav_genres_summary,
        NavAction.Route(Routes.GENRES),
    )
    NavItem.PLAYLISTS -> NavItemUi(
        AppSymbols.QueueMusic, R.string.tab_playlists, R.string.nav_playlists_summary,
        NavAction.Route(Routes.PLAYLISTS),
    )
    NavItem.FAVOURITES -> NavItemUi(
        AppSymbols.Favorite, R.string.playlist_favorites, R.string.nav_favourites_summary,
        NavAction.Route(Routes.FAVOURITES),
    )
    NavItem.ALL_SONGS -> NavItemUi(
        AppSymbols.MusicNote, R.string.playlist_all_songs, R.string.nav_all_songs_summary,
        NavAction.Route(Routes.ALL_SONGS),
    )
    NavItem.MORE -> NavItemUi(
        AppSymbols.MoreHoriz, R.string.nav_more, R.string.nav_more_summary,
        NavAction.Route(Routes.MORE),
    )
}
