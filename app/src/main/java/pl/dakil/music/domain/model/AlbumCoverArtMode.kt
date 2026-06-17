package pl.dakil.music.domain.model

/** Whether the tracks of an album share one cover art or each show their own. */
enum class AlbumCoverArtMode {
    /** All tracks display the album's shared (MediaStore) cover art. */
    SHARED,

    /** Each track displays its own embedded cover art. */
    INDIVIDUAL,
}
