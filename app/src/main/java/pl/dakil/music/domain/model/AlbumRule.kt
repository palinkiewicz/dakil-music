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
    /**
     * When true the album is not listed at all: its songs are shown under the
     * synthetic "No album" entry instead, for releases whose album tag is noise
     * (single-track downloads, "Music", …). The songs' own album tag is left alone —
     * this only changes how they are grouped, and it is exactly what [albumKey] is
     * still derived from, so the rule stays findable and reversible.
     *
     * [coverArtMode] and [authorMode] are meaningless while this is set, since the
     * album has no entry of its own to carry them: the songs move under "No album",
     * which always shows individual cover art regardless of the global setting.
     */
    val treatAsNoAlbum: Boolean = false,
) {
    /** True once every override is cleared — such a rule should be deleted. */
    val isEmpty: Boolean get() = coverArtMode == null && authorMode == null && !treatAsNoAlbum
}
