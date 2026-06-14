package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.SongTagEdit
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagWriteResult

class EditTagsUseCase(private val repository: TagEditorRepository) {
    /** Same edit applied to every song. */
    suspend operator fun invoke(songs: List<Song>, edit: TagEdit): TagWriteResult =
        repository.writeTags(songs, edit)

    /** Distinct edit per song. */
    suspend operator fun invoke(edits: List<SongTagEdit>): TagWriteResult =
        repository.writeTags(edits)
}
