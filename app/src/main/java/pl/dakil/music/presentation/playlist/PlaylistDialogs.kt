package pl.dakil.music.presentation.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.components.clickableRow

/** Single text-field dialog reused for both creating and renaming a playlist. */
@Composable
fun PlaylistNameDialog(
    titleRes: Int,
    confirmRes: Int,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_tags_cancel)) }
        },
    )
}

/**
 * Lets the user drop the chosen songs into an existing playlist or spin up a new one.
 * Switches inline to a name field when "New playlist" is picked.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onSelect: (playlistId: String) -> Unit,
    onCreateNew: (name: String) -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
        title = { Text(stringResource(R.string.add_to_playlist_title)) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.playlist_new)) },
                            leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            modifier = Modifier.clickableRow { creating = true },
                        )
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = {
                                Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                            },
                            modifier = Modifier.clickableRow { onSelect(playlist.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = { onCreateNew(newName.trim()) },
                    enabled = newName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.playlist_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_tags_cancel)) }
        },
    )
}
