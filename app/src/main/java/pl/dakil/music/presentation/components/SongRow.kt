package pl.dakil.music.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.Song
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular

/** Whether the player is playing right now, so lists can animate their current-song marker. */
val LocalIsPlaying = compositionLocalOf { false }

/** What a [SongRow] shows on its leading edge. */
sealed interface SongLeading {
    /** The song's cover (or its selection check). */
    data class Art(val model: Any?) : SongLeading

    /** The track number, for album pages where every cover would be the same. */
    data class TrackNumber(val number: Int) : SongLeading
}

/**
 * One song in a list: leading art (or track number) that turns into a check when selected and
 * into dancing bars when it's the song playing; title, artists, a favourite heart and the
 * duration. Long-press selects (with a haptic), tap plays or toggles while selecting.
 */
@Composable
fun SongRow(
    song: Song,
    leading: SongLeading,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    current: Boolean = false,
    favorite: Boolean = false,
    dragging: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    restingColor: Color = Color.Transparent,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val container by animateColorAsState(
        targetValue = when {
            dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
            selected -> MaterialTheme.colorScheme.secondaryContainer
            current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> restingColor
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "songRowColor",
    )
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "songRowElevation")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, shape)
            .clip(shape)
            .background(container)
            .combinedClickable(
                onClick = {
                    if (selectionMode) haptics.tick()
                    onClick()
                },
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            )
            .padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SongLeadingSlot(leading = leading, selected = selected, current = current)
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = if (current) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            AppIcon(
                AppSymbols.Favorite,
                contentDescription = null,
                filled = true,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

private enum class LeadingState { NORMAL, CURRENT, SELECTED }

@Composable
private fun SongLeadingSlot(leading: SongLeading, selected: Boolean, current: Boolean) {
    val isPlaying = LocalIsPlaying.current
    when (leading) {
        is SongLeading.Art -> Box(contentAlignment = Alignment.Center) {
            SelectableArt(model = leading.model, selected = selected, size = 48.dp)
            if (current && !selected) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    NowPlayingBars(isPlaying = isPlaying, color = Color.White)
                }
            }
        }

        is SongLeading.TrackNumber -> {
            val state = when {
                selected -> LeadingState.SELECTED
                current -> LeadingState.CURRENT
                else -> LeadingState.NORMAL
            }
            AnimatedContent(
                targetState = state,
                transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
                label = "trackLeading",
            ) { shown ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    when (shown) {
                        LeadingState.SELECTED -> ShapedIcon(
                            symbol = AppSymbols.Check,
                            polygon = MaterialShapes.Cookie9Sided,
                            size = 40.dp,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        LeadingState.CURRENT -> NowPlayingBars(isPlaying = isPlaying)
                        LeadingState.NORMAL -> if (leading.number > 0) {
                            Text(
                                leading.number.toString(),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AppIcon(AppSymbols.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
