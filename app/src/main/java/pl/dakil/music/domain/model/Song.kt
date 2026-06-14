package pl.dakil.music.domain.model

import android.net.Uri

/**
 * A single playable audio track resolved from [android.provider.MediaStore].
 *
 * @property artists the performer string already split into individual artists
 *   (see [pl.dakil.music.domain.util.ArtistSplitter]). [rawArtist] keeps the
 *   original, unsplit value for tag editing and display fallbacks.
 */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artists: List<String>,
    val rawArtist: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: Uri?,
    val durationMs: Long,
    val trackNumber: Int,
    val mimeType: String,
    val dateAddedSeconds: Long,
) {
    /** Convenience for single-line UI rendering of all performers. */
    val artistsLabel: String get() = artists.joinToString(", ")
}
