package pl.dakil.music.domain.model

import android.net.Uri

/** Synthetic album id grouping every song that has no album tag. */
const val NO_ALBUM_ID: Long = -1L

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
    val durationMs: Long = 0L,
) {
    val isNoAlbum: Boolean get() = id == NO_ALBUM_ID
}
