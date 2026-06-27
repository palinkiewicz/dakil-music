package pl.dakil.music.domain.model

/**
 * Filesystem-level details for a [Song], resolved on demand from MediaStore (and a
 * size/duration fallback for the bitrate). Surfaced by the "File information" dialog.
 *
 * @property bitrateBps average bitrate in bits per second, or 0 when it could not be
 *   determined.
 * @property format a short, user-facing container/codec label (e.g. "MP3", "FLAC").
 */
data class SongFileInfo(
    val song: Song,
    val path: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val bitrateBps: Int,
    val format: String,
)
