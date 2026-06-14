package pl.dakil.music.domain.model

/**
 * The two built-in playlists. Their names are resolved from string resources at the
 * UI layer so they stay localizable.
 */
enum class SystemPlaylist {
    ALL_SONGS,
    FAVORITES,
}

/**
 * A playlist entry shown in the Playlists tab — either a [systemType] (built-in) or a
 * [userPlaylist] (user-created). Exactly one of the two is non-null.
 */
data class Playlist(
    val systemType: SystemPlaylist? = null,
    val userPlaylist: UserPlaylist? = null,
    val songCount: Int = 0,
) {
    val isUser: Boolean get() = userPlaylist != null
}
