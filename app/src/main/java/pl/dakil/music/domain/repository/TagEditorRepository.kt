package pl.dakil.music.domain.repository

import pl.dakil.music.domain.model.Song

/**
 * Writes embedded audio tags into the file bytes for one or more songs.
 *
 * Modifying media the app does not own requires user consent via a system
 * [android.content.IntentSender] on API 30+ (Scoped Storage). The repository
 * returns a [TagWriteResult] so the UI can launch the consent dialog and retry the
 * whole batch.
 */
interface TagEditorRepository {

    /** Applies the same [newTags] to every song in [songs]. */
    suspend fun writeTags(songs: List<Song>, newTags: TagEdit): TagWriteResult

    /** Applies a distinct [TagEdit] per song (used by multi-song title decomposition). */
    suspend fun writeTags(edits: List<SongTagEdit>): TagWriteResult
}

/** Pairs a song with the specific edit to apply to it. */
data class SongTagEdit(val song: Song, val edit: TagEdit)

/**
 * A partial tag update: only non-null fields are written, leaving the rest intact.
 * This is what makes multi-song editing safe — untouched fields stay per-song.
 */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val album: String? = null,
    val trackNumber: String? = null,
    val year: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(title, artist, genre, album, trackNumber, year).all { it == null }
}

sealed interface TagWriteResult {
    data object Success : TagWriteResult

    /**
     * The OS requires explicit user consent. Launch this [intentSender] with an
     * [androidx.activity.result.ActivityResultLauncher] and retry on RESULT_OK.
     */
    data class RequiresPermission(val intentSender: android.content.IntentSender) : TagWriteResult

    data class Error(val throwable: Throwable) : TagWriteResult
}
