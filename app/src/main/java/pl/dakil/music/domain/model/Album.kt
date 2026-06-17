package pl.dakil.music.domain.model

import android.net.Uri

/** Synthetic album id grouping every song that has no album tag. */
const val NO_ALBUM_ID: Long = -1L

data class Album(
    val id: Long,
    val title: String,
    /** Single-line display of [authors]; the album's author per the active mode. */
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
    val durationMs: Long = 0L,
    /** Release year, or 0 when none of the album's songs carry a year tag. */
    val year: Int = 0,
    /** The album's author names per the active mode; drives "Albums authored". */
    val authors: List<String> = emptyList(),
) {
    val isNoAlbum: Boolean get() = id == NO_ALBUM_ID
}
