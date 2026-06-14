package pl.dakil.music.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.TagEdit
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.TagWriteResult

/**
 * Updates audio tags through MediaStore.
 *
 * Under Scoped Storage the app cannot silently modify media it does not own, so a
 * [SecurityException] is converted into an [IntentSender] the UI launches to ask
 * the user for write consent; the edit is retried after consent is granted.
 *
 * NOTE: this writes the MediaStore *index* columns. Persisting embedded ID3/Vorbis
 * tags into the file bytes additionally requires opening the file "rw" via a
 * [android.os.ParcelFileDescriptor] and a tag library (e.g. JAudiotagger). That
 * step is intentionally left as a documented stub.
 */
class TagEditorRepositoryImpl(
    private val context: Context,
) : TagEditorRepository {

    override suspend fun writeTags(song: Song, newTags: TagEdit): TagWriteResult =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                newTags.title?.let { put(MediaStore.Audio.Media.TITLE, it) }
                newTags.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                newTags.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            }
            if (values.size() == 0) return@withContext TagWriteResult.Success

            try {
                context.contentResolver.update(song.uri, values, null, null)
                // TODO: write embedded file tags here once a tag library is wired in.
                TagWriteResult.Success
            } catch (security: SecurityException) {
                recoverIntentSender(song.uri, security)
                    ?.let { TagWriteResult.RequiresPermission(it) }
                    ?: TagWriteResult.Error(security)
            } catch (t: Throwable) {
                TagWriteResult.Error(t)
            }
        }

    private fun recoverIntentSender(uri: Uri, security: SecurityException): IntentSender? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender

            security is RecoverableSecurityException ->
                security.userAction.actionIntent.intentSender

            else -> null
        }
}
