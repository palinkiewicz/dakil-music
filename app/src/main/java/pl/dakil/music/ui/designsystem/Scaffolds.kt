package pl.dakil.music.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import pl.dakil.music.ui.theme.Spacing

/**
 * A screen with an M3 Expressive flexible top app bar that collapses as the content scrolls:
 * [large] for tab roots (big rounded title), medium for pushed screens. Content must scroll
 * vertically (verticalScroll or a lazy list) for the bar to collapse.
 */
@Composable
fun CollapsingScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    large: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleContent: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    // A changing subtitle (edit-mode hints, counts) cross-fades rather than snapping.
    val subtitleContent: (@Composable () -> Unit)? = subtitle?.let {
        {
            AnimatedContent(targetState = subtitle, label = "subtitle") { text ->
                Text(text.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        topBar = {
            if (large) {
                LargeFlexibleTopAppBar(
                    title = titleContent,
                    subtitle = subtitleContent,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors = appTopBarColors(),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumFlexibleTopAppBar(
                    title = titleContent,
                    subtitle = subtitleContent,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors = appTopBarColors(),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = content,
    )
}

/**
 * The scrolling body of a page: phone gutters, a readable max width centred on wide windows, and
 * extra room at the bottom so the last item can scroll clear of floating UI.
 */
@Composable
fun PageColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.l,
    bottomPadding: Dp = Spacing.xxl,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}
