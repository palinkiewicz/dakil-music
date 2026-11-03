package pl.dakil.music.presentation.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavItem
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.navigation.NavAction
import pl.dakil.music.presentation.navigation.navItemUi
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.entrance
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedItemShape
import pl.dakil.music.ui.theme.SegmentedShapes
import pl.dakil.music.ui.theme.Spacing

/**
 * The More tab: the app's badge (a slowly turning Cookie) with its name and promise, then the
 * user-configured shortcuts as one segmented card.
 */
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
    val haptics = rememberHaptics()
    var showAbout by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { res ->
            haptics.confirm()
            snackbarHostState.showSnackbar(resources.getString(res))
        }
    }

    // Re-tapping the More tab scrolls back to the top.
    LaunchedEffect(onReselect) {
        onReselect.collect { listState.animateScrollToItem(0) }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        ) {
            item(key = "hero") { MoreHero() }
            itemsIndexed(items, key = { _, item -> item.name }) { index, item ->
                val ui = navItemUi(item)
                val isRefreshRow = ui.action is NavAction.Refresh
                MoreItem(
                    icon = ui.icon,
                    title = stringResource(ui.labelRes),
                    summary = stringResource(
                        if (isRefreshRow && isRefreshing) R.string.refresh_in_progress else ui.summaryRes,
                    ),
                    index = index,
                    count = items.size,
                    busy = isRefreshRow && isRefreshing,
                    showChevron = ui.action is NavAction.Route,
                    onClick = {
                        when (ui.action) {
                            NavAction.Refresh -> {
                                haptics.tick()
                                viewModel.refreshLibrary()
                            }
                            NavAction.About -> showAbout = true
                            is NavAction.Route -> onOpen(item)
                        }
                    },
                    modifier = Modifier.entrance(index + 1),
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun MoreHero() {
    val spin = rememberInfiniteTransition(label = "badgeSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "badgeAngle",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Spacing.xxl, bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ShapedIcon(
            symbol = AppSymbols.LibraryMusic,
            polygon = MaterialShapes.Cookie12Sided,
            size = 112.dp,
            iconSize = 48.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerRotation = angle,
            modifier = Modifier
                .padding(bottom = Spacing.s)
                .entrance(0),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmallEmphasized)
        Text(
            stringResource(R.string.more_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
    }
}

@Composable
private fun MoreItem(
    icon: AppSymbol,
    title: String,
    summary: String,
    index: Int,
    count: Int,
    busy: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = segmentedItemShape(index, count),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ShapedIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                AnimatedContent(summary, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "moreSummary") {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                busy -> LoadingIndicator(Modifier.size(32.dp))
                showChevron -> AppIcon(AppSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
