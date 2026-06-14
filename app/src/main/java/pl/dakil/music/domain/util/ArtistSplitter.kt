package pl.dakil.music.domain.util

/**
 * Splits a raw artist/author metadata string into individual performers.
 *
 * MediaStore stores collaborations as a single concatenated string such as
 * "Daft Punk, Pharrell Williams feat. Nile Rodgers". To index each contributor
 * separately under the Performers tab we split on the common separators
 * `", "`, `"; "` and `"feat. "` (case-insensitive), then trim and de-duplicate.
 *
 * Pure and side-effect free so it can be unit-tested without Android.
 */
object ArtistSplitter {

    // Punctuation separators (",", ";", "&") may hug the names; keyword separators
    // ("feat", "ft", "featuring", "with", "x", "vs") must be standalone words — the
    // surrounding `\s+` stops e.g. the "ft" inside "Daft Punk" from matching.
    private val DELIMITERS = Regex(
        pattern = """\s*[,;&]\s*|\s+(?:feat\.?|ft\.?|featuring|with|x|vs\.?)\s+""",
        option = RegexOption.IGNORE_CASE,
    )

    fun split(rawArtist: String?): List<String> {
        if (rawArtist.isNullOrBlank()) return emptyList()
        return DELIMITERS.split(rawArtist)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }
}
