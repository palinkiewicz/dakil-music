package pl.dakil.music.presentation.songlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import pl.dakil.music.R
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.presentation.components.albumAuthorModeNameRes

/**
 * Edits album-wide tags (title/year) plus the album's optional custom cover-art and
 * author rule. The rule fields are disabled until "Use custom settings" is on.
 */
@Composable
fun EditAlbumDialog(
    dialog: SongDialog.EditAlbum,
    onDismiss: () -> Unit,
    onSave: (title: String, year: String, useCustom: Boolean, coverArt: AlbumCoverArtMode, author: AlbumAuthorMode) -> Unit,
) {
    var title by remember { mutableStateOf(dialog.album.title) }
    var year by remember { mutableStateOf(dialog.album.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var useCustom by remember { mutableStateOf(dialog.rule != null) }
    var sharedCover by remember {
        mutableStateOf((dialog.rule?.coverArtMode ?: dialog.globalCoverArtMode) == AlbumCoverArtMode.SHARED)
    }
    var author by remember { mutableStateOf(dialog.rule?.authorMode ?: dialog.globalAuthorMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.album_edit_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { new -> year = new.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.album_edit_field_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.album_edit_use_custom), modifier = Modifier.weight(1f))
                    Switch(checked = useCustom, onCheckedChange = { useCustom = it })
                }
                Column(modifier = Modifier.alpha(if (useCustom) 1f else 0.38f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.settings_album_shared_cover_art),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = sharedCover,
                            onCheckedChange = { sharedCover = it },
                            enabled = useCustom,
                        )
                    }
                    AuthorModeSelector(
                        selected = author,
                        enabled = useCustom,
                        onSelect = { author = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    title,
                    year,
                    useCustom,
                    if (sharedCover) AlbumCoverArtMode.SHARED else AlbumCoverArtMode.INDIVIDUAL,
                    author,
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AuthorModeSelector(
    selected: AlbumAuthorMode,
    enabled: Boolean,
    onSelect: (AlbumAuthorMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // A select field matching the Settings dropdowns: label + current value + chevron.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true },
    ) {
        Text(
            text = stringResource(R.string.settings_album_author_mode),
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(albumAuthorModeNameRes(selected)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                AlbumAuthorMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(albumAuthorModeNameRes(mode))) },
                        onClick = {
                            onSelect(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Renames a performer; the change is retagged across every song they appear on. */
@Composable
fun EditArtistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.artist_edit_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.artist_edit_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
