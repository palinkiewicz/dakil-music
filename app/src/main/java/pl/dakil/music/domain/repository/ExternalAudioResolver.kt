package pl.dakil.music.domain.repository

import android.net.Uri
import pl.dakil.music.domain.model.Song

/**
 * Turns an externally supplied audio [Uri] (ACTION_VIEW) into a [Song]: a library
 * match when the uri points at an indexed track, otherwise a synthetic song built
 * from the file's own metadata.
 */
interface ExternalAudioResolver {
    suspend fun resolve(uri: Uri, librarySongs: List<Song>): Song
}
