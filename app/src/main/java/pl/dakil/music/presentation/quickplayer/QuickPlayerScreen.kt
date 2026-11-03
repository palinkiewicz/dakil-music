package pl.dakil.music.presentation.quickplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.presentation.components.AlbumArt
import pl.dakil.music.presentation.components.TimeBar
import pl.dakil.music.presentation.components.coverArtModel
import pl.dakil.music.presentation.nowplaying.ArtTheme
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing

/**
 * Stripped-down player for a single externally opened audio file: artwork, track
 * info, seekbar and basic transport. Deliberately no queue, lyrics, favorites or
 * bottom navigation — this screen is an isolated, transient viewer. It speaks the full
 * player's language (cover colors, wavy seek bar, morphing play button) so it feels like Music.
 */
@Composable
fun QuickPlayerScreen(
    state: QuickPlayerUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val song = state.song
    val haptics = rememberHaptics()
    ArtTheme(artModel = song?.coverArtModel()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onOpenApp, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(end = Spacing.s)) {
                    AppIcon(AppSymbols.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.quick_player_open_app))
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth - Spacing.xl * 2, maxHeight - Spacing.l)
                val restScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1f else 0.9f,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "quickArtRest",
                )
                AlbumArt(
                    model = song?.coverArtModel(),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .size(side)
                        .graphicsLayer {
                            scaleX = restScale
                            scaleY = restScale
                        },
                )
            }

            Column(Modifier.padding(horizontal = Spacing.xl)) {
                Text(
                    text = song?.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.quick_player_unknown_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                )
                Text(
                    text = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.s))
                TimeBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = onSeek,
                    isPlaying = state.isPlaying,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                WideningSlot(1f) { interaction ->
                    FilledTonalIconButton(
                        onClick = {
                            haptics.tick()
                            onSeekBy(-SEEK_STEP_MS)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxSize(0.78f).fillMaxWidth(),
                    ) {
                        AppIcon(AppSymbols.Replay10, stringResource(R.string.cd_seek_back), modifier = Modifier.size(30.dp))
                    }
                }
                WideningSlot(1.5f) { interaction ->
                    FilledIconToggleButton(
                        checked = state.isPlaying,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onPlayPause()
                        },
                        shapes = IconButtonDefaults.toggleableShapes(
                            shape = RoundedCornerShape(50),
                            pressedShape = RoundedCornerShape(20.dp),
                            checkedShape = RoundedCornerShape(28.dp),
                        ),
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                            label = "quickPlayPause",
                        ) { playing ->
                            AppIcon(
                                if (playing) AppSymbols.Pause else AppSymbols.PlayArrow,
                                contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                                filled = true,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                WideningSlot(1f) { interaction ->
                    FilledTonalIconButton(
                        onClick = {
                            haptics.tick()
                            onSeekBy(SEEK_STEP_MS)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxSize(0.78f).fillMaxWidth(),
                    ) {
                        AppIcon(AppSymbols.Forward10, stringResource(R.string.cd_seek_forward), modifier = Modifier.size(30.dp))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

/** A transport slot that widens while its button is held (M3 Expressive press-to-widen). */
@Composable
private fun RowScope.WideningSlot(baseWeight: Float, content: @Composable (MutableInteractionSource) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * 1.25f else baseWeight,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "quickWiden",
    )
    Box(Modifier.weight(weight).fillMaxSize(), contentAlignment = Alignment.Center) { content(interaction) }
}

private const val SEEK_STEP_MS = 10_000L
