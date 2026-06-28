package pl.dakil.music.presentation.more

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import pl.dakil.music.R
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.clickableRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenListeningHistory: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
    viewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { res ->
            snackbarHostState.showSnackbar(context.getString(res))
        }
    }

    // Re-tapping the More tab scrolls back to the top.
    LaunchedEffect(onReselect) {
        onReselect.collect { listState.animateScrollToItem(0) }
    }

    Scaffold(
        modifier = modifier,
        // The host already insets for the bottom navigation bar; don't add it twice.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.more_title)) })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Group 1: history & statistics
            item {
                MoreItem(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.more_listening_history),
                    summary = stringResource(R.string.more_listening_history_summary),
                    onClick = onOpenListeningHistory,
                )
            }
            item {
                MoreItem(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.more_statistics),
                    summary = stringResource(R.string.more_statistics_summary),
                    onClick = onOpenStatistics,
                )
            }
            item { HorizontalDivider() }
            // Group 2: library refresh
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
            // Group 3: settings & backup
            item {
                MoreItem(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.more_settings),
                    summary = stringResource(R.string.more_settings_summary),
                    onClick = onOpenSettings,
                )
            }
            item {
                MoreItem(
                    icon = Icons.Rounded.Inventory2,
                    title = stringResource(R.string.more_backup),
                    summary = stringResource(R.string.more_backup_summary),
                    onClick = onOpenBackup,
                )
            }
            item { HorizontalDivider() }
            // Group 4: about
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
