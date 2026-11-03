package pl.dakil.music.presentation.songlist

import androidx.compose.foundation.layout.Row
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

    FormSheet(
        title = stringResource(R.string.edit_tags_title),
        symbol = AppSymbols.Label,
        supportingText = if (multi) stringResource(R.string.edit_tags_multi_notice, songs.size) else null,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.edit_tags_cancel),
        confirmLabel = stringResource(R.string.edit_tags_save),
        onConfirm = {
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
        },
    ) {
        TagField(title, { title = it }, R.string.edit_tags_field_title)
        TagField(artist, { artist = it }, R.string.edit_tags_field_artist)
        TagField(album, { album = it }, R.string.edit_tags_field_album)
        TagField(genre, { genre = it }, R.string.edit_tags_field_genre)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TagField(
                track, { track = it.filter(Char::isDigit) },
                R.string.edit_tags_field_track, numeric = true,
                modifier = Modifier.weight(1f),
            )
            TagField(
                year, { year = it.filter(Char::isDigit) },
                R.string.edit_tags_field_year, numeric = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TagField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    numeric: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
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
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
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
