package pl.dakil.music.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

private enum class NavTab(@param:StringRes val titleRes: Int, val component: NavComponent) {
    BOTTOM_BAR(R.string.nav_tab_bottom_bar, NavComponent.BOTTOM_BAR),
    LIBRARY_TABS(R.string.nav_tab_library, NavComponent.LIBRARY_TABS),
    MORE_SCREEN(R.string.nav_tab_more, NavComponent.MORE_SCREEN),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NavigationCustomizationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavigationCustomizationViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val tabs = NavTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_navigation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val component = tabs[page].component
                ReorderableToggleList(
                    rows = rowsFor(config, component),
                    onToggle = { item, enabled -> viewModel.setEnabled(component, item, enabled) },
                    onMove = { from, to -> viewModel.move(component, from, to) },
                )
            }
        }
    }
}

@Composable
private fun rowsFor(config: NavConfig, component: NavComponent): List<ToggleRow> {
    val locked = NavRules.lockedItems(config, component)
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
