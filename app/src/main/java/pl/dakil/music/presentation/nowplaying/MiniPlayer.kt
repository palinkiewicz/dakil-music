package pl.dakil.music.presentation.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.ui.designsystem.MorphShape
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import kotlin.math.abs

/** How far (dp) the mini player must be flung sideways before letting go skips a song. */
private const val SKIP_THRESHOLD_DP = 72f

/**
 * The collapsed player docked above the navigation bar. The art blooms from a soft square into a
 * slowly turning Cookie while music plays; a wavy line along the bottom tracks progress. Swipe
 * sideways to skip, tap or swipe up (handled by the sheet) to open the full player.
 */
@Composable
fun MiniPlayer(
    state: NowPlayingUiState,
    artVisible: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { SKIP_THRESHOLD_DP.dp.toPx() }
    val swipe = remember { Animatable(0f) }
    var armed by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.hasNext, state.hasPrevious) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val offset = swipe.value
                            scope.launch {
                                when {
                                    offset <= -threshold && state.hasNext -> onNext()
                                    offset >= threshold && state.hasPrevious -> onPrevious()
                                }
                                armed = 0f
                                swipe.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    ) { change, delta ->
                        change.consume()
                        // Resist past the threshold so the row doesn't fly off.
                        val next = swipe.value + delta * if (abs(swipe.value) > threshold) 0.3f else 1f
                        val nowArmed = when {
                            next <= -threshold && state.hasNext -> -1f
                            next >= threshold && state.hasPrevious -> 1f
                            else -> 0f
                        }
                        if (nowArmed != armed) {
                            if (nowArmed != 0f) haptics.threshold()
                            armed = nowArmed
                        }
                        scope.launch { swipe.snapTo(next) }
                    }
                }
                .graphicsLayer { translationX = swipe.value }
                .padding(start = Spacing.s, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MiniArt(
                model = song.coverArtModel(),
                playing = state.isPlaying,
                modifier = Modifier
                    .size(MiniArtSize)
                    .graphicsLayer { alpha = if (artVisible) 1f else 0f },
            )
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                contentKey = { it.id },
                modifier = Modifier.weight(1f),
                label = "miniSong",
            ) { current ->
                Column {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                    )
                    Text(
                        current.artistsLabel.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledIconToggleButton(
                checked = state.isPlaying,
                onCheckedChange = {
                    haptics.toggle(it)
                    onPlayPause()
                },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
            ) {
                AppIcon(
                    if (state.isPlaying) AppSymbols.Pause else AppSymbols.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    filled = true,
                )
            }
            IconButton(
                onClick = {
                    haptics.tick()
                    onNext()
                },
                enabled = state.hasNext,
                shapes = IconButtonDefaults.shapes(),
            ) {
                AppIcon(AppSymbols.SkipNext, contentDescription = stringResource(R.string.cd_next), filled = true)
            }
        }

        val progress = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val animatedProgress by animateFloatAsState(progress, tween(500, easing = LinearEasing), label = "miniProgress")
        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Under the text column, clear of the art.
                .padding(start = Spacing.s + MiniArtSize + Spacing.m, end = Spacing.l, bottom = Spacing.xs)
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            amplitude = { if (state.isPlaying) 0.5f else 0f },
            wavelength = 18.dp,
            waveSpeed = if (state.isPlaying) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
            gapSize = 2.dp,
            stopSize = 0.dp,
        )
    }
}

/** The mini player's art: a soft square at rest that blooms into a slowly turning Cookie in play. */
@Composable
private fun MiniArt(model: Any?, playing: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }
    val bloom by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "miniBloom",
    )
    val spin = rememberInfiniteTransition(label = "miniSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "miniAngle",
    )
    // The clip turns with the shape while the picture counter-rotates, so the cover stays upright
    // inside the turning Cookie (scaled up a little so its corners never show through the bumps).
    Box(
        modifier
            .graphicsLayer { rotationZ = if (playing) angle * bloom else 0f }
            .clip(MorphShape(morph, bloom)),
    ) {
        AlbumArt(
            model = model,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = if (playing) -angle * bloom else 0f
                    val cover = 1f + 0.18f * bloom
                    scaleX = cover
                    scaleY = cover
                },
        )
    }
}
