package pl.dakil.music.domain.model

/** Immutable snapshot of the Media3 player, surfaced to the UI as a single state object. */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** The full play queue in order, and the index of [currentSong] within it. */
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
)

enum class RepeatMode { OFF, ALL, ONE }
