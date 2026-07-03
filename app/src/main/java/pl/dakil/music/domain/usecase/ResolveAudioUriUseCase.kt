package pl.dakil.music.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.first
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.ExternalAudioResolver
import pl.dakil.music.domain.repository.MusicRepository

/**
 * Resolves an externally opened audio uri (ACTION_VIEW) into a playable [Song],
 * preferring an existing library track over a synthetic one.
 */
class ResolveAudioUriUseCase(
    private val resolver: ExternalAudioResolver,
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(uri: Uri): Song =
        resolver.resolve(uri, musicRepository.songs.first())
}
