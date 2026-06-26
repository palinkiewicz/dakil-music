package pl.dakil.music.presentation.lyrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.data.playback.LyricsStatus
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showAlignDialog by remember { mutableStateOf(false) }
    var showLrclibDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.retryPendingBurn()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LyricsEvent.RequestWritePermission ->
                    permissionLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())

                is LyricsEvent.Message ->
                    snackbarHostState.showSnackbar(context.getString(event.resId))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.synced) {
                        IconButton(onClick = { showAlignDialog = true }) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = stringResource(R.string.lyrics_align),
                            )
                        }
                    }
                    if (state.lrclibEnabled) {
                        IconButton(onClick = { showLrclibDialog = true }) {
                            Icon(
                                Icons.Rounded.Lyrics,
                                contentDescription = stringResource(R.string.lyrics_select_lrclib),
                            )
                        }
                    }
                    if (state.canBurn) {
                        IconButton(onClick = viewModel::burn) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = stringResource(R.string.lyrics_burn),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                LyricsStatus.SEARCHING -> CenteredMessage { CircularProgressIndicator() }
                LyricsStatus.NOT_FOUND -> CenteredMessage {
                    Text(
                        stringResource(R.string.lyrics_not_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LyricsStatus.FOUND -> LyricsBody(
                    state = state,
                    onSeekLine = viewModel::onSeekToLine,
                )
            }
        }
    }

    if (showAlignDialog) {
        AlignDialog(
            offsetMs = state.offsetMs,
            onOffsetChange = viewModel::setOffset,
            onNudge = viewModel::nudgeOffset,
            onDismiss = { showAlignDialog = false },
        )
    }

    if (showLrclibDialog) {
        LrclibPickerDialog(
            defaultArtist = state.defaultArtist,
            defaultTrack = state.defaultTrack,
            matches = state.matches,
            onSearch = viewModel::search,
            onSelect = {
                viewModel.selectMatch(it)
                showLrclibDialog = false
            },
            onDismiss = { showLrclibDialog = false },
        )
    }
}

@Composable
private fun LyricsBody(
    state: LyricsScreenState,
    onSeekLine: (pl.dakil.music.domain.model.LyricLine) -> Unit,
) {
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // User scrolling disengages auto-follow; the sync FAB re-engages it.
    LaunchedEffect(dragged) { if (dragged) autoScroll = false }

    // Keep the active synced line centered while following.
    LaunchedEffect(state.activeLineIndex, autoScroll, state.synced) {
        if (state.synced && autoScroll && state.activeLineIndex >= 0) {
            val viewportH = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(state.activeLineIndex, -viewportH / 3)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.lines) { index, line ->
                val active = index == state.activeLineIndex
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = if (state.synced) TextAlign.Center else TextAlign.Start,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        active -> MaterialTheme.colorScheme.primary
                        state.synced -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (state.synced && line.timeMs != null) {
                                Modifier.clickable { onSeekLine(line) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 2.dp),
                )
            }
        }

        if (state.synced && !autoScroll) {
            FloatingActionButton(
                onClick = {
                    autoScroll = true
                    if (state.activeLineIndex >= 0) {
                        scope.launch {
                            val viewportH = listState.layoutInfo.viewportSize.height
                            listState.animateScrollToItem(state.activeLineIndex, -viewportH / 3)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = stringResource(R.string.lyrics_resync))
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AlignDialog(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onNudge: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(offsetMs) { mutableStateOf(offsetMs.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_align)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.lyrics_align_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onNudge(-STEP_MS) }) { Text("-") }
                    Slider(
                        value = offsetMs.toFloat(),
                        onValueChange = { onOffsetChange((it / STEP_MS).toLong() * STEP_MS) },
                        valueRange = -RANGE_MS.toFloat()..RANGE_MS.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onNudge(STEP_MS) }) { Text("+") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        text = new
                        new.toLongOrNull()?.let { onOffsetChange(it) }
                    },
                    label = { Text(stringResource(R.string.lyrics_align_field)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun LrclibPickerDialog(
    defaultArtist: String,
    defaultTrack: String,
    matches: List<LrclibMatch>,
    onSearch: (String, String) -> Unit,
    onSelect: (LrclibMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    var artist by remember { mutableStateOf(defaultArtist) }
    var track by remember { mutableStateOf(defaultTrack) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_select_lrclib)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text(stringResource(R.string.lyrics_field_artist)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = track,
                            onValueChange = { track = it },
                            label = { Text(stringResource(R.string.lyrics_field_track)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onSearch(artist, track) }) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.lyrics_search),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (matches.isEmpty()) {
                    Text(
                        stringResource(R.string.lyrics_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(matches) { match ->
                            LrclibMatchRow(match = match, onClick = { onSelect(match) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun LrclibMatchRow(match: LrclibMatch, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = match.trackName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = match.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = buildString {
                append(formatDuration((match.durationSec * 1000).toLong()))
                if (match.albumName.isNotBlank()) append("  •  ").append(match.albumName)
                append("  •  #").append(match.id)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val STEP_MS = 100L
private const val RANGE_MS = 10_000L
