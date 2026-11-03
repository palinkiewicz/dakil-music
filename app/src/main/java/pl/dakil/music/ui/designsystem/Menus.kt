package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols

/**
 * An M3 Expressive menu anchored to its parent layout. Put one or more [AppMenuGroup]s inside; a
 * single group reads as one rounded card, several as a stack of connected cards.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
            MenuAnchorPosition.Below,
            offset,
        ),
        content = content,
    )
}

/** One group of an [AppMenu]; [index] and [count] place it among the menu's groups. */
@Composable
fun AppMenuGroup(index: Int = 0, count: Int = 1, content: @Composable ColumnScope.() -> Unit) {
    if (index > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index, count), content = content)
}

/** A plain action row. [destructive] tints it with the error color. */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    symbol: AppSymbol? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = if (destructive) {
        MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        )
    } else {
        MenuDefaults.itemColors()
    }
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text) },
        shape = MenuDefaults.itemShapes().shape,
        leadingIcon = symbol?.let { { AppIcon(it, contentDescription = null) } },
        enabled = enabled,
        colors = colors,
    )
}

/** A single-choice row (sort order, unit): the selected one fills and shows a check. */
@Composable
fun AppMenuChoiceItem(text: String, selected: Boolean, onClick: () -> Unit, symbol: AppSymbol? = null) {
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = { Text(text) },
        shapes = MenuDefaults.itemShapes(),
        leadingIcon = symbol?.let { { AppIcon(it, contentDescription = null) } },
        selectedLeadingIcon = { AppIcon(AppSymbols.Check, contentDescription = null, modifier = Modifier.size(24.dp)) },
    )
}

/** An on/off row (word wrap, show hidden). */
@Composable
fun AppMenuCheckItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, symbol: AppSymbol? = null) {
    val haptics = rememberHaptics()
    CheckableDropdownMenuItem(
        checked = checked,
        onCheckedChange = {
            haptics.toggle(it)
            onCheckedChange(it)
        },
        text = { Text(text) },
        shapes = MenuDefaults.itemShapes(),
        leadingIcon = symbol?.let { { AppIcon(it, contentDescription = null) } },
        checkedLeadingIcon = { AppIcon(AppSymbols.Check, contentDescription = null) },
    )
}

/** A small primary-coloured caption heading a group's items. */
@Composable
fun AppMenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
