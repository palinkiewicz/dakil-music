package pl.dakil.music.domain.model

import android.net.Uri

/**
 * One listening session. See the data-layer entity for storage details. [artists]
 * and the other snapshot fields reflect the song's tags at the moment it was
 * played, so deleted songs remain displayable.
 */
data class ListeningRecord(
    val id: Long = 0,
    val songId: Long,
    val startTimestamp: Long,
    val secondsPlayed: Int,
    val timesPlayed: Int,
    val title: String,
    val artists: List<String>,
    val album: String,
    val albumId: Long,
    val albumArtUri: Uri?,
    val durationMs: Long,
    val contentKey: String,
) {
    val artistsLabel: String get() = artists.joinToString(", ")
}
