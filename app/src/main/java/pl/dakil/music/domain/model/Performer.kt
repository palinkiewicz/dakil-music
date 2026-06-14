package pl.dakil.music.domain.model

/**
 * An individual performer indexed from the split artist metadata. A song credited
 * to "A, B feat. C" contributes to three distinct performers.
 */
data class Performer(
    val name: String,
    val songCount: Int,
)
