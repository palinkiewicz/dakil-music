package pl.dakil.music.presentation.songlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.TagEdit

/**
 * Material 3 dialog for editing a track's title/artist/album. Only changed fields
 * are forwarded as a [TagEdit] so untouched tags are left intact.
 */
@Composable
fun EditTagsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (TagEdit) -> Unit,
) {
    var title by rememberSaveable(song.id) { mutableStateOf(song.title) }
    var artist by rememberSaveable(song.id) { mutableStateOf(song.rawArtist) }
    var album by rememberSaveable(song.id) { mutableStateOf(song.album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_tags_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_tags_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.edit_tags_field_artist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(R.string.edit_tags_field_album)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TagEdit(
                        title = title.takeIf { it != song.title },
                        artist = artist.takeIf { it != song.rawArtist },
                        album = album.takeIf { it != song.album },
                    ),
                )
            }) {
                Text(stringResource(R.string.edit_tags_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_tags_cancel))
            }
        },
    )
}
