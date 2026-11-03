package pl.dakil.music.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes

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

    FormSheet(
        title = title,
        symbol = AppSymbols.PlaylistAdd,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_cancel),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.song_search_hint)) },
            leadingIcon = { AppIcon(AppSymbols.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        ) {
            itemsIndexed(filtered, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    leading = SongLeading.Art(song.coverArtModel()),
                    onClick = { onPick(song) },
                    onLongClick = { onPick(song) },
                    shape = segmentedItemShape(index, filtered.size),
                    restingColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
        }
    }
}
