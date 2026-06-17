package pl.dakil.music.domain.model

/**
 * How an album's displayed author is derived from the artists of its songs.
 *
 * The choice also drives which albums appear under an artist's "Albums authored"
 * section: an album is authored by an artist when that artist is among the names
 * this mode produces.
 */
enum class AlbumAuthorMode {
    /** Every artist of the album's first song. */
    FIRST_SONG_ARTISTS,

    /** Only the first artist of the album's first song. */
    FIRST_ARTIST_OF_FIRST_SONG,

    /** The single artist appearing on the most songs in the album. */
    MOST_COMMON,

    /** Every distinct artist appearing anywhere in the album. */
    ALL_ARTISTS,
}
