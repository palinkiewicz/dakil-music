package pl.dakil.music.presentation.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.dakil.music.BuildConfig
import pl.dakil.music.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME) +
                    "\n\n" + stringResource(R.string.about_description),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}
