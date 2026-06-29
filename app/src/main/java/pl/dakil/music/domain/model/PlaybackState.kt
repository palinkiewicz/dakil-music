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
    /** Playback speed multiplier (1.0 = normal). Session-only; never persisted. */
    val playbackSpeed: Float = 1f,
    /** Milliseconds left on the active timed sleep timer, or null when none is running. */
    val sleepTimerRemainingMs: Long? = null,
    /** Active non-timed sleep mode (stop at end of track/queue), or null when none is armed. */
    val sleepTimerMode: SleepTimerMode? = null,
) {
    /** True when any kind of sleep timer (timed or end-of-track/queue) is active. */
    val sleepTimerActive: Boolean get() = sleepTimerRemainingMs != null || sleepTimerMode != null
}

enum class RepeatMode { OFF, ALL, ONE }

/** Non-timed sleep modes that pause playback at a natural boundary instead of after a duration. */
enum class SleepTimerMode { END_OF_TRACK, END_OF_QUEUE }
