package pl.dakil.music.domain.model

/** A user-created playlist: a stable id, a renameable name and an ordered song list. */
data class UserPlaylist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
)
