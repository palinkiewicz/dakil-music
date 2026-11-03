package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.theme.Spacing

/**
 * The app's frame for forms and choices that used to be dialogs: a modal sheet with a shaped icon,
 * a big title, an optional supporting line, scrolling content and a dismiss/confirm button pair.
 * Passing no [confirmLabel] leaves a single full-width dismiss button.
 */
@Composable
fun FormSheet(
    title: String,
    symbol: AppSymbol,
    onDismiss: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    // The secondary button's action when it isn't simply "dismiss" (a two-way choice).
    onDismissButton: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ShapedIcon(
                    symbol = symbol,
                    polygon = MaterialShapes.Cookie9Sided,
                    size = 48.dp,
                    containerColor = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.weight(1f))
            }
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = onDismissButton, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text(dismissLabel, maxLines = 1)
                }
                if (confirmLabel != null) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onConfirm()
                        },
                        enabled = confirmEnabled,
                        shapes = ButtonDefaults.shapes(),
                        colors = if (destructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(confirmLabel, maxLines = 1)
                    }
                }
            }
        }
    }
}
