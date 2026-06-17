package pl.dakil.music.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song

/**
 * Searches the library by title/artist and invokes [onPick] for the chosen song.
 * Mirrors the merge-with-existing search used by listening history, reused here to
 * add a song to a playlist (or favorites).
 */
@Composable
fun SongPickerDialog(
    title: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPick: (Song) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, songs) {
        val q = query.trim().lowercase()
        val sorted = songs.sortedBy { it.title.lowercase() }
        if (q.isEmpty()) sorted
        else sorted.filter { song ->
            song.title.lowercase().contains(q) || song.artists.any { it.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.song_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        ListItem(
                            leadingContent = {
                                AlbumArt(
                                    model = song.coverArtModel(),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.size(40.dp),
                                )
                            },
                            headlineContent = {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    song.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                        ?: stringResource(R.string.unknown_artist),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickableRow { onPick(song) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
