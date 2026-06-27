package pl.dakil.music.presentation.components

import android.content.Context
import android.content.Intent
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song

/**
 * Opens the native Android share sheet for [songs]. Shares the MediaStore content URIs
 * directly (granting temporary read access), using ACTION_SEND for one file and
 * ACTION_SEND_MULTIPLE for several.
 */
fun shareSongs(context: Context, songs: List<Song>) {
    if (songs.isEmpty()) return

    val uris = songs.map { it.uri }
    // A shared type across mixed formats; collapses to a common prefix when possible.
    val mime = songs.map { it.mimeType }.distinct().singleOrNull() ?: "audio/*"

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(intent, context.getString(R.string.share_chooser_title))
    // Started from a non-Activity context in some call paths.
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
