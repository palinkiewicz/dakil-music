package pl.dakil.music.domain.model

/**
 * A per-album override of the global cover-art / author settings.
 *
 * Keyed by [albumKey] — a normalized content identity (see
 * [pl.dakil.music.domain.util.AlbumKey]) so the rule survives MediaStore
 * reassigning the album's numeric id. A null field means "no override for that
 * aspect"; the global setting applies instead.
 */
data class AlbumRule(
    val albumKey: String,
    val coverArtMode: AlbumCoverArtMode? = null,
    val authorMode: AlbumAuthorMode? = null,
) {
    /** True once every override is cleared — such a rule should be deleted. */
    val isEmpty: Boolean get() = coverArtMode == null && authorMode == null
}
