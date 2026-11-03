package pl.dakil.music.ui.designsystem

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.dakil.music.R
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/**
 * Top app bar colors: the bar blends with the screen while content is at rest and lifts to a
 * tonal container once content scrolls under it.
 */
@Composable
fun appTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/** The navigation icon of every pushed screen. */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
        AppIcon(AppSymbols.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}
