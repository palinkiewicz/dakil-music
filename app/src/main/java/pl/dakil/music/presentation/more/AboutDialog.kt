package pl.dakil.music.presentation.more

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.dakil.music.BuildConfig
import pl.dakil.music.R
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing

/** About the app: what it is and which version is installed. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    FormSheet(
        title = stringResource(R.string.about_title),
        symbol = AppSymbols.LibraryMusic,
        supportingText = stringResource(R.string.about_description),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.about_close),
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
}
