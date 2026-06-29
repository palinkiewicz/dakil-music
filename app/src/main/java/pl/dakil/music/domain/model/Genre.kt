package pl.dakil.music.domain.model

/**
 * A distinct genre indexed from each song's [Song.genre] tag. Songs with no genre
 * tag are not grouped under any genre (mirroring how untagged artists are skipped).
 */
data class Genre(
    val name: String,
    val songCount: Int,
)
