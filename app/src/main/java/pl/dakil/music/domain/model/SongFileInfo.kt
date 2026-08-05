package pl.dakil.music.domain.model

/**
 * Filesystem-level details for a [Song], resolved on demand from MediaStore (with the
 * bitrate probed from the file itself). Surfaced by the "File information" dialog.
 *
 * @property bitrateBps bitrate of the audio stream in bits per second — an average for
 *   VBR files — or 0 when it could not be determined.
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
