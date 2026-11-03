package pl.dakil.music.presentation.lyrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.data.playback.LyricsStatus
import pl.dakil.music.domain.model.LyricLine
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.presentation.components.formatDuration
import pl.dakil.music.ui.designsystem.AppSlider
import pl.dakil.music.ui.designsystem.EmptyState
import pl.dakil.music.ui.designsystem.RollingNumber
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing

/**
 * Routes [LyricsViewModel] events: write-permission requests launch the system prompt (and retry
 * the burn once granted), messages go to [snackbarHostState].
 */
@Composable
fun LyricsEventsEffect(viewModel: LyricsViewModel, snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.retryPendingBurn()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LyricsEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())

                is LyricsEvent.Message -> {
                    if (event.resId == R.string.lyrics_burn_success) haptics.confirm() else haptics.reject()
                    snackbarHostState.showSnackbar(resources.getString(event.resId))
                }
            }
        }
    }
}

/**
 * Full-height lyrics for the player. Synced lyrics follow the playhead, the active line growing
 * into focus while the rest recede; dragging takes over the scroll and a resync button hands it
 * back. Tapping a synced line seeks to it.
 */
@Composable
fun LyricsPane(
    state: LyricsScreenState,
    onSeekLine: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    onSearchLrclib: (() -> Unit)? = null,
) {
    AnimatedContent(
        targetState = state.status,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
        modifier = modifier,
        label = "lyricsStatus",
    ) { status ->
        when (status) {
            LyricsStatus.SEARCHING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        stringResource(R.string.lyrics_searching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LyricsStatus.NOT_FOUND -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    symbol = AppSymbols.Lyrics,
                    title = stringResource(R.string.lyrics_not_found),
                    polygon = MaterialShapes.Flower,
                    actions = onSearchLrclib?.let { search ->
                        {
                            FilledTonalButton(onClick = search, shapes = ButtonDefaults.shapes()) {
                                AppIcon(AppSymbols.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(Spacing.s))
                                Text(stringResource(R.string.lyrics_select_lrclib))
                            }
                        }
                    },
                )
            }

            LyricsStatus.FOUND -> LyricsBody(state = state, onSeekLine = onSeekLine, fadeColor = fadeColor)
        }
    }
}

@Composable
private fun LyricsBody(
    state: LyricsScreenState,
    onSeekLine: (LyricLine) -> Unit,
    fadeColor: Color,
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // User scrolling disengages auto-follow; the sync button re-engages it.
    LaunchedEffect(dragged) { if (dragged) autoScroll = false }

    // Keep the active synced line in the upper part of the pane while following. Scrolling to an
    // item rests it right below the top content padding, so that padding alone sets where it sits
    // (an extra negative offset pushed it down onto the song title).
    LaunchedEffect(state.activeLineIndex, autoScroll, state.synced) {
        if (state.synced && autoScroll && state.activeLineIndex >= 0) {
            listState.animateScrollToItem(state.activeLineIndex)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = maxHeight * ACTIVE_LINE_FRACTION,
                // Room for the last line to reach the resting position too.
                bottom = maxHeight * (1f - ACTIVE_LINE_FRACTION),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            itemsIndexed(state.lines) { index, line ->
                LyricLineText(
                    text = line.text.ifBlank { "♪" },
                    synced = state.synced,
                    active = index == state.activeLineIndex,
                    past = state.synced && index < state.activeLineIndex,
                    onClick = if (state.synced && line.timeMs != null) {
                        {
                            haptics.tick()
                            autoScroll = true
                            onSeekLine(line)
                        }
                    } else {
                        null
                    },
                )
            }
        }
        // Lines fade into the player's background at both edges.
        EdgeFade(fadeColor, top = true, Modifier.align(Alignment.TopCenter))
        EdgeFade(fadeColor, top = false, Modifier.align(Alignment.BottomCenter))

        AnimatedVisibility(
            visible = state.synced && !autoScroll,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.l),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    haptics.tick()
                    autoScroll = true
                    if (state.activeLineIndex >= 0) {
                        scope.launch {
                            listState.animateScrollToItem(state.activeLineIndex)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                AppIcon(AppSymbols.Sync, contentDescription = stringResource(R.string.lyrics_resync))
            }
        }
    }
}

@Composable
private fun LyricLineText(
    text: String,
    synced: Boolean,
    active: Boolean,
    past: Boolean,
    onClick: (() -> Unit)?,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (!synced || active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "lineEmphasis",
    )
    val color by animateColorAsState(
        targetValue = when {
            !synced -> MaterialTheme.colorScheme.onSurface
            active -> MaterialTheme.colorScheme.primary
            past -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "lineColor",
    )
    Text(
        text = text,
        style = if (synced) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = 0.9f + 0.1f * emphasis
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.xxs),
    )
}

@Composable
private fun EdgeFade(color: Color, top: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                Brush.verticalGradient(
                    colors = if (top) listOf(color, Color.Transparent) else listOf(Color.Transparent, color),
                ),
            ),
    )
}

/** Shifts synced lyrics against the audio: ±100 ms nudges, a ±10 s slider, or a typed value. */
@Composable
fun LyricsAlignSheet(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onNudge: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var text by remember(offsetMs) { mutableStateOf(offsetMs.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ShapedIcon(
                symbol = AppSymbols.Timer,
                polygon = MaterialShapes.Cookie9Sided,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(stringResource(R.string.lyrics_align), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                stringResource(R.string.lyrics_align_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RollingNumber(
                text = formatOffset(offsetMs),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.tick()
                        onNudge(-STEP_MS)
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                AppSlider(
                    value = offsetMs.toFloat(),
                    onValueChange = { onOffsetChange((it / STEP_MS).toLong() * STEP_MS) },
                    valueRange = -RANGE_MS.toFloat()..RANGE_MS.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = {
                        haptics.tick()
                        onNudge(STEP_MS)
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    new.toLongOrNull()?.let { onOffsetChange(it) }
                },
                label = { Text(stringResource(R.string.lyrics_align_field)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

/** Searches lrclib.net and lets the user pick which result to use for the current song. */
@Composable
fun LrclibSheet(
    defaultArtist: String,
    defaultTrack: String,
    matches: List<LrclibMatch>,
    searching: Boolean,
    onSearch: (String, String) -> Unit,
    onSelect: (LrclibMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    var artist by remember { mutableStateOf(defaultArtist) }
    var track by remember { mutableStateOf(defaultTrack) }

    // Auto-search on open when nothing was fetched yet (e.g. lyrics came from
    // metadata, so lrclib was never queried for this song).
    LaunchedEffect(Unit) {
        if (matches.isEmpty() && !searching) onSearch(artist, track)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.lyrics_select_lrclib), style = MaterialTheme.typography.headlineSmallEmphasized)
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text(stringResource(R.string.lyrics_field_artist)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = track,
                onValueChange = { track = it },
                label = { Text(stringResource(R.string.lyrics_field_track)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(artist, track) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSearch(artist, track) },
                enabled = !searching,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIcon(AppSymbols.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.s))
                Text(stringResource(R.string.lyrics_search))
            }
            when {
                searching -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                matches.isEmpty() -> Text(
                    stringResource(R.string.lyrics_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.l),
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    items(matches) { match -> LrclibMatchRow(match = match, onClick = { onSelect(match) }) }
                }
            }
        }
    }
}

@Composable
private fun LrclibMatchRow(match: LrclibMatch, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            Text(
                text = match.trackName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = match.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(formatDuration((match.durationSec * 1000).toLong()))
                    if (match.albumName.isNotBlank()) append("  ·  ").append(match.albumName)
                    append("  ·  #").append(match.id)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** An offset with an explicit sign and unit, e.g. "+0.3 s", "−1.2 s". */
private fun formatOffset(offsetMs: Long): String {
    val seconds = "%.1f".format(kotlin.math.abs(offsetMs) / 1000f)
    val sign = when {
        offsetMs > 0 -> "+"
        offsetMs < 0 -> "−"
        else -> ""
    }
    return "$sign$seconds s"
}

/** Where the active synced line rests, as a fraction of the pane's height from the top. */
private const val ACTIVE_LINE_FRACTION = 0.28f

private const val STEP_MS = 100L
private const val RANGE_MS = 10_000L
