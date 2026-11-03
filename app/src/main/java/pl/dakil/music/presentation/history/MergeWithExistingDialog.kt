package pl.dakil.music.presentation.history

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
import pl.dakil.music.presentation.components.SongLeading
import pl.dakil.music.presentation.components.SongRow
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes

/**
 * Lets the user pick a live song to fold a deleted record's plays into. Selecting a
 * song asks for confirmation before the merge is committed.
 */
@Composable
fun MergeWithExistingDialog(
    label: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onMerge: (Song) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingTarget by remember { mutableStateOf<Song?>(null) }

    val filtered = remember(query, songs) {
        val q = query.trim().lowercase()
        val sorted = songs.sortedBy { it.title.lowercase() }
        if (q.isEmpty()) sorted
        else sorted.filter { song ->
            song.title.lowercase().contains(q) || song.artists.any { it.lowercase().contains(q) }
        }
    }

    val target = pendingTarget
    if (target != null) {
        FormSheet(
            title = stringResource(R.string.history_merge_confirm_title),
            symbol = AppSymbols.CallSplit,
            supportingText = stringResource(R.string.history_merge_confirm_message, label, target.title),
            onDismiss = { pendingTarget = null },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.history_merge_confirm_action),
            onConfirm = { onMerge(target) },
        )
        return
    }

    FormSheet(
        title = stringResource(R.string.history_merge_title),
        symbol = AppSymbols.MusicOff,
        supportingText = label,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_cancel),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.history_merge_search_hint)) },
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
                    onClick = { pendingTarget = song },
                    onLongClick = { pendingTarget = song },
                    shape = segmentedItemShape(index, filtered.size),
                    restingColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
        }
    }
}
