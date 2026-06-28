package pl.dakil.music.presentation.navigation

import android.net.Uri
import pl.dakil.music.domain.model.SystemPlaylist

/** Type-safe-ish route helpers for the Navigation-Compose graph. */
object Routes {
    const val NOW_PLAYING = "now_playing"
    const val LIBRARY = "library"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val ALBUM_RULES = "album_rules"
    const val LISTENING_HISTORY = "listening_history"
    const val STATISTICS = "statistics"
    const val LYRICS = "lyrics"
    const val BACKUP = "backup"

    // Shared song-list detail screen, parameterised by its source.
    const val SONG_LIST = "song_list"
    const val ARG_SOURCE_TYPE = "sourceType"
    const val ARG_SOURCE_ARG = "sourceArg"
    const val SONG_LIST_PATTERN = "$SONG_LIST/{$ARG_SOURCE_TYPE}/{$ARG_SOURCE_ARG}"

    fun albumSongs(albumId: Long) = "$SONG_LIST/${SourceType.ALBUM}/$albumId"

    fun performerSongs(name: String) =
        "$SONG_LIST/${SourceType.PERFORMER}/${Uri.encode(name)}"

    fun playlistSongs(playlist: SystemPlaylist) =
        "$SONG_LIST/${SourceType.PLAYLIST}/${playlist.name}"

    fun userPlaylistSongs(id: String) =
        "$SONG_LIST/${SourceType.PLAYLIST}/${Uri.encode(id)}"
}

enum class SourceType { ALBUM, PERFORMER, PLAYLIST }

/** Decoded representation of a song-list source, reconstructed inside the ViewModel. */
sealed interface SongListSource {
    data class AlbumSource(val albumId: Long) : SongListSource
    data class PerformerSource(val name: String) : SongListSource
    data class PlaylistSource(val playlist: SystemPlaylist) : SongListSource
    data class UserPlaylistSource(val playlistId: String) : SongListSource
}
