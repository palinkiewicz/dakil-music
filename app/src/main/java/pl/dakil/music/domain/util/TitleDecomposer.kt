package pl.dakil.music.domain.util

/**
 * Options driving [TitleDecomposer]. Defaults match the most common YouTube-rip
 * pattern: "Author1, Author2 - Song name (feat. Author3)".
 */
data class DecomposeOptions(
    /** Separator between the authors block and the song name, e.g. "-". */
    val mainSeparator: String = "-",
    /** Whether the authors block sits before [mainSeparator] (true) or after it. */
    val authorsBeforeSeparator: Boolean = true,
    /** Also pull "feat./ft./with" performers out of the song name. */
    val extractFeat: Boolean = true,
    /** If non-blank, drop this substring and everything after it from the title
     *  (e.g. enter "(" to strip "(Official Video)"). */
    val removeAfter: String = "",
)

data class DecomposeResult(
    val title: String,
    val artists: List<String>,
)

/**
 * Pulls performers out of a messy track title and returns the cleaned title plus the
 * extracted artists. Pure and Android-free so it can be unit-tested and previewed live.
 */
object TitleDecomposer {

    // Matches a leading "feat./ft./featuring/with" marker, optionally opened by ( or [.
    // No trailing \b: it would fail after the optional dot in "feat. ".
    private val FEAT_MARKER = Regex(
        pattern = """\s*[(\[]?\s*\b(?:featuring|feat\.?|ft\.?|with)\s+""",
        option = RegexOption.IGNORE_CASE,
    )

    fun decompose(rawTitle: String, options: DecomposeOptions): DecomposeResult {
        var working = rawTitle.trim()
        val artists = LinkedHashSet<String>()

        // 1. Trim trailing noise such as "(Official Video)".
        if (options.removeAfter.isNotBlank()) {
            val idx = working.indexOf(options.removeAfter, ignoreCase = true)
            if (idx >= 0) working = working.substring(0, idx).trim()
        }

        // 2. Split the authors block off the title using the main separator.
        var titlePart = working
        if (options.mainSeparator.isNotEmpty()) {
            val idx = working.indexOf(options.mainSeparator)
            if (idx >= 0) {
                val left = working.substring(0, idx).trim()
                val right = working.substring(idx + options.mainSeparator.length).trim()
                if (options.authorsBeforeSeparator) {
                    artists += ArtistSplitter.split(left)
                    titlePart = right
                } else {
                    artists += ArtistSplitter.split(right)
                    titlePart = left
                }
            }
        }

        // 3. Extract any "feat." performers still embedded in the title.
        if (options.extractFeat) {
            val match = FEAT_MARKER.find(titlePart)
            if (match != null) {
                val before = titlePart.substring(0, match.range.first).trim()
                val after = titlePart.substring(match.range.last + 1)
                    .trim()
                    .trimEnd(')', ']', ' ')
                artists += ArtistSplitter.split(after)
                titlePart = before
            }
        }

        return DecomposeResult(
            title = titlePart.trim(),
            artists = artists.toList(),
        )
    }
}
