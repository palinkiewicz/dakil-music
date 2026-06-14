package pl.dakil.music.presentation.songlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.util.DecomposeOptions
import pl.dakil.music.domain.util.TitleDecomposer

/**
 * Lets the user pull performers out of a messy (e.g. YouTube-ripped) title and move
 * them into the artist tag. The chosen options are applied to every song in [songs];
 * the live preview reflects the first one.
 */
@Composable
fun DecomposeTitleDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onApply: (DecomposeOptions) -> Unit,
) {
    val previewSong = songs.firstOrNull() ?: return
    val key = previewSong.id

    var mainSeparator by rememberSaveable(key) { mutableStateOf("-") }
    var authorsBefore by rememberSaveable(key) { mutableStateOf(true) }
    var extractFeat by rememberSaveable(key) { mutableStateOf(true) }
    var removeAfter by rememberSaveable(key) { mutableStateOf("") }

    val options = DecomposeOptions(
        mainSeparator = mainSeparator,
        authorsBeforeSeparator = authorsBefore,
        extractFeat = extractFeat,
        removeAfter = removeAfter,
    )
    val result = remember(previewSong.title, options) {
        TitleDecomposer.decompose(previewSong.title, options)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decompose_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (songs.size > 1) {
                    Text(
                        text = stringResource(R.string.decompose_multi_notice, songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LabeledValue(stringResource(R.string.decompose_original), previewSong.title)

                OutlinedTextField(
                    value = mainSeparator,
                    onValueChange = { mainSeparator = it },
                    label = { Text(stringResource(R.string.decompose_main_separator)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    label = stringResource(R.string.decompose_authors_before),
                    checked = authorsBefore,
                    onCheckedChange = { authorsBefore = it },
                )
                SwitchRow(
                    label = stringResource(R.string.decompose_extract_feat),
                    checked = extractFeat,
                    onCheckedChange = { extractFeat = it },
                )
                OutlinedTextField(
                    value = removeAfter,
                    onValueChange = { removeAfter = it },
                    label = { Text(stringResource(R.string.decompose_remove_after)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Live preview.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.decompose_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LabeledValue(
                            stringResource(R.string.edit_tags_field_title),
                            result.title.ifBlank { "—" },
                        )
                        LabeledValue(
                            stringResource(R.string.edit_tags_field_artist),
                            result.artists.joinToString(", ").ifBlank { "—" },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(options) },
                enabled = result.artists.isNotEmpty(),
            ) {
                Text(stringResource(R.string.decompose_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_tags_cancel))
            }
        },
    )
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
