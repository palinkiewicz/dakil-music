package pl.dakil.music.presentation.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.UserPlaylist
import pl.dakil.music.presentation.components.tonalPairFor
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing

/** Names a new playlist or renames one. */
@Composable
fun PlaylistNameDialog(
    titleRes: Int,
    confirmRes: Int,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    FormSheet(
        title = stringResource(titleRes),
        symbol = AppSymbols.QueueMusic,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.edit_tags_cancel),
        confirmLabel = stringResource(confirmRes),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name.trim()) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.playlist_name_label)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
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

    FormSheet(
        title = stringResource(R.string.add_to_playlist_title),
        symbol = AppSymbols.PlaylistAdd,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.edit_tags_cancel),
        confirmLabel = if (creating) stringResource(R.string.playlist_create) else null,
        confirmEnabled = newName.isNotBlank(),
        onConfirm = { onCreateNew(newName.trim()) },
    ) {
        AnimatedContent(
            targetState = creating,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "addToPlaylist",
        ) { isCreating ->
            if (isCreating) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank()) onCreateNew(newName.trim()) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap)) {
                    val count = playlists.size + 1
                    PlaylistChoice(
                        name = stringResource(R.string.playlist_new),
                        index = 0,
                        count = count,
                        isNew = true,
                        onClick = { creating = true },
                    )
                    playlists.forEachIndexed { i, playlist ->
                        PlaylistChoice(
                            name = playlist.name,
                            index = i + 1,
                            count = count,
                            onClick = { onSelect(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoice(name: String, index: Int, count: Int, onClick: () -> Unit, isNew: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = segmentedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (isNew) {
                ShapedIcon(
                    symbol = AppSymbols.Add,
                    polygon = MaterialShapes.Cookie9Sided,
                    size = 40.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                val (container, content) = tonalPairFor(name)
                ShapedIcon(
                    symbol = AppSymbols.QueueMusic,
                    polygon = MaterialShapes.Square,
                    size = 40.dp,
                    containerColor = container,
                    contentColor = content,
                )
            }
            Text(
                name,
                style = if (isNew) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
