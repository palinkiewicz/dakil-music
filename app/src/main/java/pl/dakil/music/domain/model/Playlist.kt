package pl.dakil.music.domain.model

/**
 * System-defined playlists. Names are resolved from string resources at the UI
 * layer (see [nameRes]) so they remain fully localizable.
 */
enum class SystemPlaylist {
    ALL_SONGS,
    FAVORITES,
}

data class Playlist(
    val type: SystemPlaylist,
    val songCount: Int,
)
