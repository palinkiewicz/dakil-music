package pl.dakil.music.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.domain.model.NavComponent
import pl.dakil.music.domain.model.NavConfig
import pl.dakil.music.domain.model.NavDefaults
import pl.dakil.music.domain.model.NavRules
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.ReorderableToggleList
import pl.dakil.music.presentation.components.ToggleRow
import pl.dakil.music.presentation.navigation.navItemUi
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.ConnectedToggleRow
import pl.dakil.music.ui.designsystem.ToggleOption
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing

private enum class NavTab(@param:StringRes val titleRes: Int, val component: NavComponent, val symbol: AppSymbol) {
    BOTTOM_BAR(R.string.nav_tab_bottom_bar, NavComponent.BOTTOM_BAR, AppSymbols.DockToBottom),
    LIBRARY_TABS(R.string.nav_tab_library, NavComponent.LIBRARY_TABS, AppSymbols.LibraryMusic),
    MORE_SCREEN(R.string.nav_tab_more, NavComponent.MORE_SCREEN, AppSymbols.MoreHoriz),
}

/**
 * Customizes the three navigation surfaces. A connected toggle row picks the surface; each page
 * is a reorderable list of on/off tiles.
 */
@Composable
fun NavigationCustomizationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavigationCustomizationViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val miniPlayerShown by viewModel.miniPlayerShown.collectAsStateWithLifecycle()
    val tabs = NavTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    CollapsingScaffold(
        title = stringResource(R.string.settings_navigation),
        subtitle = stringResource(R.string.settings_navigation_summary),
        navigationIcon = { BackButton(onBack) },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConnectedToggleRow(
                options = tabs.map { ToggleOption(it, stringResource(it.titleRes), it.symbol) },
                selected = tabs[pagerState.currentPage],
                onSelect = { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            )
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val component = tabs[page].component
                ReorderableToggleList(
                    rows = rowsFor(config, component, miniPlayerShown),
                    onToggle = { item, enabled -> viewModel.setEnabled(component, item, enabled) },
                    onMove = { from, to -> viewModel.move(component, from, to) },
                )
            }
        }
    }
}

@Composable
private fun rowsFor(config: NavConfig, component: NavComponent, miniPlayerShown: Boolean): List<ToggleRow> {
    val locked = NavRules.lockedItems(config, component, miniPlayerShown)
    val atCapacity = config.enabled(NavComponent.BOTTOM_BAR).size >= NavDefaults.MAX_BOTTOM_BAR
    return config.entries(component).map { entry ->
        val ui = navItemUi(entry.item)
        val switchEnabled = when {
            entry.item in locked -> false
            component == NavComponent.BOTTOM_BAR && !entry.enabled && atCapacity -> false
            else -> true
        }
        ToggleRow(
            item = entry.item,
            label = stringResource(ui.labelRes),
            icon = ui.icon,
            enabled = entry.enabled,
            switchEnabled = switchEnabled,
        )
    }
}
