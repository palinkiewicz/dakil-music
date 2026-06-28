package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.data.backup.PreferencesBackupCodec
import pl.dakil.music.domain.model.BackupCategory
import pl.dakil.music.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backs the [BackupRepository] with the app's Preferences DataStores (one per
 * [BackupCategory], serialized generically by [PreferencesBackupCodec]). A full
 * backup is simply every category's document bundled into one zip.
 */
class BackupRepositoryImpl(
    private val stores: Map<BackupCategory, DataStore<Preferences>>,
) : BackupRepository {

    override suspend fun exportCategory(category: BackupCategory, out: OutputStream) {
        withContext(Dispatchers.IO) {
            out.use { it.write(PreferencesBackupCodec.export(store(category)).encodeToByteArray()) }
        }
    }

    override suspend fun importCategory(category: BackupCategory, input: InputStream) {
        withContext(Dispatchers.IO) {
            // A single-file import trusts the *contents*, not the document name: the
            // codec validates the text and rejects (throws on) anything that isn't a
            // valid backup, leaving the existing data untouched.
            val text = input.use { it.readBytes().decodeToString() }
            PreferencesBackupCodec.import(store(category), text)
        }
    }

    override suspend fun exportFull(out: OutputStream) {
        withContext(Dispatchers.IO) {
            ZipOutputStream(out).use { zip ->
                for (category in BackupCategory.entries) {
                    zip.putNextEntry(ZipEntry(category.fileName))
                    zip.write(PreferencesBackupCodec.export(store(category)).encodeToByteArray())
                    zip.closeEntry()
                }
            }
        }
    }

    override suspend fun importFull(input: InputStream) {
        withContext(Dispatchers.IO) {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // A zip can hold many files, so here we *do* route by entry name.
                    val category = BackupCategory.byFileName(entry.name)
                    if (category != null) {
                        PreferencesBackupCodec.import(store(category), zip.readBytes().decodeToString())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun store(category: BackupCategory): DataStore<Preferences> =
        stores[category] ?: error("No DataStore registered for $category")
}
