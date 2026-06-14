package pl.dakil.music.domain.repository

import pl.dakil.music.domain.model.Song

/**
 * Writing tags to media the app does not own requires user consent via a system
 * [android.content.IntentSender] on API 30+ (Scoped Storage). The repository
 * returns a [TagWriteResult] so the UI can launch the consent dialog and retry.
 */
interface TagEditorRepository {

    suspend fun writeTags(song: Song, newTags: TagEdit): TagWriteResult
}

/** Only non-null fields are written. */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

sealed interface TagWriteResult {
    data object Success : TagWriteResult

    /**
     * The OS requires explicit user consent. Launch this [intentSender] with an
     * [androidx.activity.result.ActivityResultLauncher] and retry on RESULT_OK.
     */
    data class RequiresPermission(val intentSender: android.content.IntentSender) : TagWriteResult

    data class Error(val throwable: Throwable) : TagWriteResult
}
