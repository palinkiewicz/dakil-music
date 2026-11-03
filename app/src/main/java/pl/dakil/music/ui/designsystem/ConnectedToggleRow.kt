package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.dakil.music.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol

@Immutable
data class ToggleOption<T>(
    val value: T,
    val label: String,
    val symbol: AppSymbol? = null,
    /** Show only the symbol; [label] then becomes its content description. */
    val iconOnly: Boolean = false,
)

/**
 * M3 Expressive connected button group used as a single-select segmented control: the checked
 * button morphs to a fully round shape and the neighbours keep their connected inner corners.
 */
@Composable
fun <T> ConnectedToggleRow(
    options: List<ToggleOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    colors: ToggleButtonColors = raisedToggleColors(),
) {
    val haptics = rememberHaptics()
    Row(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option.value == selected
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val content: @Composable RowScope.() -> Unit = {
                option.symbol?.let {
                    AnimatedAppIcon(
                        it,
                        contentDescription = if (option.iconOnly) option.label else null,
                        filled = checked,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!option.iconOnly) Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                if (!option.iconOnly) {
                    // Shrinks a long label (Polish "Systemowy") rather than clipping it.
                    Text(
                        option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                    )
                }
            }
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        haptics.tick()
                        onSelect(option.value)
                    }
                },
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier).semantics { role = Role.RadioButton },
                shapes = shapes,
                colors = colors,
                content = content,
            )
        }
    }
}
