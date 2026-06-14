package pl.dakil.music.presentation.songlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.domain.repository.TagEdit

/**
 * Material 3 dialog for editing track tags. In single-song mode fields are prefilled
 * and only *changed* fields are written. In multi-song mode fields start blank and
 * only *filled* fields are applied to every selected song (a notice explains this).
 */
@Composable
fun EditTagsDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (TagEdit) -> Unit,
) {
    val multi = songs.size > 1
    val base = songs.firstOrNull()
    val seed = if (multi) null else base
    val key = songs.joinToString(",") { it.id.toString() }

    var title by rememberSaveable(key) { mutableStateOf(seed?.title.orEmpty()) }
    var artist by rememberSaveable(key) { mutableStateOf(seed?.rawArtist.orEmpty()) }
    var genre by rememberSaveable(key) { mutableStateOf(seed?.genre.orEmpty()) }
    var album by rememberSaveable(key) { mutableStateOf(seed?.album.orEmpty()) }
    var track by rememberSaveable(key) { mutableStateOf(seed?.trackNumber.nonZeroOrEmpty()) }
    var year by rememberSaveable(key) { mutableStateOf(seed?.year.nonZeroOrEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_tags_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (multi) {
                    Text(
                        text = stringResource(R.string.edit_tags_multi_notice, songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TagField(title, { title = it }, R.string.edit_tags_field_title)
                TagField(artist, { artist = it }, R.string.edit_tags_field_artist)
                TagField(genre, { genre = it }, R.string.edit_tags_field_genre)
                TagField(album, { album = it }, R.string.edit_tags_field_album)
                TagField(
                    track, { track = it.filter(Char::isDigit) },
                    R.string.edit_tags_field_track, numeric = true,
                )
                TagField(
                    year, { year = it.filter(Char::isDigit) },
                    R.string.edit_tags_field_year, numeric = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    buildEdit(
                        multi = multi,
                        seed = seed,
                        title = title,
                        artist = artist,
                        genre = genre,
                        album = album,
                        track = track,
                        year = year,
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

@Composable
private fun TagField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Builds the partial update. Multi-edit forwards only non-blank fields; single-edit
 * forwards only fields that actually changed from the original tag.
 */
private fun buildEdit(
    multi: Boolean,
    seed: Song?,
    title: String,
    artist: String,
    genre: String,
    album: String,
    track: String,
    year: String,
): TagEdit {
    fun field(value: String, original: String?): String? = if (multi) {
        value.takeIf { it.isNotBlank() }
    } else {
        value.takeIf { it != original.orEmpty() }
    }

    return TagEdit(
        title = field(title, seed?.title),
        artist = field(artist, seed?.rawArtist),
        genre = field(genre, seed?.genre),
        album = field(album, seed?.album),
        trackNumber = field(track, seed?.trackNumber.nonZeroOrEmpty()),
        year = field(year, seed?.year.nonZeroOrEmpty()),
    )
}

private fun Int?.nonZeroOrEmpty(): String = if (this != null && this != 0) toString() else ""
