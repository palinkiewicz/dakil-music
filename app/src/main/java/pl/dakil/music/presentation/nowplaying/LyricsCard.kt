package pl.dakil.music.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.data.playback.LyricsStatus
import androidx.compose.ui.res.stringResource

private val CARD_HEIGHT = 176.dp
private val FADE_HEIGHT = 40.dp

/**
 * Compact, fixed-height lyrics preview shown under the Now Playing controls. The
 * content fades into the card's own color at the top and bottom to hint that
 * tapping opens the full [LyricsScreen]. Renders three states: searching, lyrics,
 * and not-found. For synced lyrics the active line is kept centered with a quick
 * scroll animation as it advances.
 */
@Composable
fun LyricsCard(
    state: LyricsCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT)
                .clickable(enabled = state.status == LyricsStatus.FOUND, onClick = onClick),
        ) {
            when (state.status) {
                LyricsStatus.SEARCHING -> Searching()
                LyricsStatus.NOT_FOUND -> Centered(stringResource(R.string.lyrics_not_found))
                LyricsStatus.FOUND -> {
                    LyricsScroller(state)
                    FadeEdge(cardColor, top = true, Modifier.align(Alignment.TopCenter))
                    FadeEdge(cardColor, top = false, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun Searching() {
    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.lyrics_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsScroller(state: LyricsCardState) {
    val listState = rememberLazyListState()

    // Keep the active synced line vertically centered, animating as it advances.
    // The half-card vertical padding lets even the first/last line reach the center.
    LaunchedEffect(state.synced, state.activeLineIndex, state.lines.size) {
        if (!state.synced || state.activeLineIndex !in state.lines.indices) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == state.activeLineIndex }) {
            listState.scrollToItem(state.activeLineIndex)
        }
        val info = listState.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == state.activeLineIndex }
            ?: return@LaunchedEffect
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val itemCenter = target.offset + target.size / 2f
        listState.animateScrollBy(itemCenter - viewportCenter)
    }

    val verticalPadding = if (state.synced) CARD_HEIGHT / 2 else 12.dp
    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(state.lines) { index, line ->
            val active = index == state.activeLineIndex
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = if (state.synced) TextAlign.Center else TextAlign.Start,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A gradient strip fading the card color to transparent at the top or bottom edge. */
@Composable
private fun FadeEdge(color: Color, top: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FADE_HEIGHT)
            .background(
                Brush.verticalGradient(
                    colors = if (top) listOf(color, Color.Transparent) else listOf(Color.Transparent, color),
                ),
            ),
    )
}
