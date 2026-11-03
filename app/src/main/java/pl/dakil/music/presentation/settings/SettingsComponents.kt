package pl.dakil.music.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import pl.dakil.music.ui.designsystem.AppMenu
import pl.dakil.music.ui.designsystem.AppMenuChoiceItem
import pl.dakil.music.ui.designsystem.AppMenuGroup
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.PageColumn
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.checkableItemColors
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.designsystem.rememberHaptics
import pl.dakil.music.ui.designsystem.segmentedInnerShapes
import pl.dakil.music.ui.designsystem.segmentedItemColors
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular

// ---------------------------------------------------------------------------
// Shared settings building blocks. Rows are segmented tonal tiles: put them in a
// SegmentedGroup so consecutive rows read as one rounded card.
// ---------------------------------------------------------------------------

/** The shell of every settings subscreen: a collapsing medium title over a scrolling page. */
@Composable
fun SettingsSubscreen(
    title: String,
    onNavigateBack: () -> Unit,
    showBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    CollapsingScaffold(
        title = title,
        large = !showBack,
        navigationIcon = { if (showBack) BackButton(onNavigateBack) },
    ) { padding ->
        PageColumn(padding, spacing = Spacing.xs, content = content)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.xs, end = Spacing.l)
            .semantics { heading() },
    )
}

/**
 * Shared base for every settings row, so switches, sliders and selects share
 * the exact same paddings and sizing. [trailing] is sized to its own content.
 */
@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val supportingContent: (@Composable () -> Unit)? = if (summary != null || supporting != null) {
        {
            Column {
                summary?.let { Text(it) }
                supporting?.invoke()
            }
        }
    } else null
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = segmentedInnerShapes(),
            enabled = enabled,
            colors = segmentedItemColors(),
            leadingContent = leading,
            supportingContent = supportingContent,
            trailingContent = trailing,
        ) { Text(title) }
    } else {
        SegmentedListItem(
            shapes = segmentedInnerShapes(),
            enabled = enabled,
            colors = segmentedItemColors(),
            leadingContent = leading,
            supportingContent = supportingContent,
            trailingContent = trailing,
        ) { Text(title) }
    }
}

@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val toggle = { value: Boolean ->
        haptics.toggle(value)
        onCheckedChange(value)
    }
    SegmentedListItem(
        checked = checked,
        onCheckedChange = toggle,
        shapes = segmentedInnerShapes(),
        enabled = enabled,
        colors = checkableItemColors(),
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                // The whole row is the toggle; the switch only mirrors it.
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { AppIcon(AppSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
    ) { Text(title) }
}

@Composable
fun SliderRow(
    title: String,
    summary: String? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    steps: Int = valueRange.last - valueRange.first - 1,
    valueLabel: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val sliderState = remember(valueRange, steps) {
        SliderState(
            value = value.toFloat(),
            steps = steps,
            trackRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        )
    }
    // Follow the setting when it changes from anywhere other than this slider.
    LaunchedEffect(value) {
        if (sliderState.value.roundToInt() != value) sliderState.value = value.toFloat()
    }
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        leading = leading,
        supporting = {
            Slider(
                state = sliderState,
                onValueChange = {
                    sliderState.value = it
                    val stepped = it.roundToInt()
                    if (stepped != value) {
                        haptics.tick()
                        onValueChange(stepped)
                    }
                },
                enabled = enabled,
            )
        },
        trailing = {
            Text(
                valueLabel(value),
                style = MaterialTheme.typography.titleMediumEmphasized.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
fun <T> SelectRow(
    title: String,
    summary: String? = null,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { expanded = true },
        trailing = {
            // Menu anchored to this trailing box so it opens on the right.
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AppIcon(AppSymbols.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                AppMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    AppMenuGroup {
                        options.forEach { (value, label) ->
                            AppMenuChoiceItem(
                                text = label,
                                selected = label == selectedLabel,
                                onClick = {
                                    if (label != selectedLabel) haptics.tick()
                                    onSelect(value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** A row that opens another screen: a shaped icon, and a trailing chevron. */
@Composable
fun NavigationRow(
    title: String,
    summary: String? = null,
    icon: AppSymbol? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = icon?.let { { ShapedIcon(it) } },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(end = Spacing.s)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    )
                }
                AppIcon(
                    AppSymbols.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
