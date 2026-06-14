package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.UserPlaylist

/** Persists user-created playlists (DataStore-backed). */
interface UserPlaylistRepository {

    val playlists: Flow<List<UserPlaylist>>

    /** Creates an empty playlist and returns its generated id. */
    suspend fun create(name: String): String

    suspend fun rename(id: String, newName: String)

    suspend fun delete(id: String)

    /** Appends [songIds] to the playlist, ignoring ids already present. */
    suspend fun addSongs(id: String, songIds: List<Long>)
}
