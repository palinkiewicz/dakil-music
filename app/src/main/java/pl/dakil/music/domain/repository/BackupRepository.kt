package pl.dakil.music.domain.repository

import pl.dakil.music.domain.model.BackupCategory
import java.io.InputStream
import java.io.OutputStream

/**
 * Imports and exports app data — settings, favorites, playlists and the other
 * persisted slices listed in [BackupCategory] — as plain documents.
 *
 * Each method fully owns the stream it is given and closes it when done. Importing a
 * category *replaces* that category's stored data. Implementations run their own IO.
 */
interface BackupRepository {

    /** Writes a single [category] as CSV to [out]. */
    suspend fun exportCategory(category: BackupCategory, out: OutputStream)

    /** Replaces a single [category] from a CSV [input] previously produced by export. */
    suspend fun importCategory(category: BackupCategory, input: InputStream)

    /** Writes every category into a single zip document on [out]. */
    suspend fun exportFull(out: OutputStream)

    /** Restores every category found in a zip [input], ignoring unknown entries. */
    suspend fun importFull(input: InputStream)
}
