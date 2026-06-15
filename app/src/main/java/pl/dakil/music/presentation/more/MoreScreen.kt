package pl.dakil.music.presentation.more

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pl.dakil.music.R
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.clickableRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { res ->
            snackbarHostState.showSnackbar(context.getString(res))
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.more_title)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                MoreItem(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.more_settings),
                    summary = stringResource(R.string.more_settings_summary),
                    onClick = onOpenSettings,
                )
            }
            item { HorizontalDivider() }
            item {
                MoreItem(
                    icon = Icons.Rounded.Refresh,
                    title = stringResource(R.string.more_refresh),
                    summary = stringResource(
                        if (isRefreshing) R.string.refresh_in_progress else R.string.more_refresh_summary,
                    ),
                    onClick = viewModel::refreshLibrary,
                    trailing = {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                MoreItem(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.more_about),
                    summary = stringResource(R.string.more_about_summary),
                    onClick = { showAbout = true },
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = Modifier.clickableRow(onClick),
    )
}
