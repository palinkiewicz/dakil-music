package pl.dakil.music.presentation.more

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.clickableRow
import pl.dakil.music.presentation.navigation.NavAction
import pl.dakil.music.presentation.navigation.navItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpen: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: Flow<Unit> = emptyFlow(),
    viewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showAbout by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { res ->
            snackbarHostState.showSnackbar(resources.getString(res))
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
            items(items, key = { it.name }) { item ->
                val ui = navItemUi(item)
                val isRefreshRow = ui.action is NavAction.Refresh
                MoreItem(
                    icon = ui.icon,
                    title = stringResource(ui.labelRes),
                    summary = stringResource(
                        if (isRefreshRow && isRefreshing) R.string.refresh_in_progress else ui.summaryRes,
                    ),
                    onClick = {
                        when (ui.action) {
                            NavAction.Refresh -> viewModel.refreshLibrary()
                            NavAction.About -> showAbout = true
                            is NavAction.Route -> onOpen(item)
                        }
                    },
                    trailing = {
                        if (isRefreshRow && isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
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
