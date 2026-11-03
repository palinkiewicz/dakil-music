package pl.dakil.music.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.theme.Spacing

/** A centred "nothing here" block: a big shaped symbol, a title, a body and optional actions. */
@Composable
fun EmptyState(
    symbol: AppSymbol,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    polygon: RoundedPolygon = MaterialShapes.Cookie12Sided,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ShapedIcon(
            symbol = symbol,
            polygon = polygon,
            size = 96.dp,
            iconSize = 44.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Column(Modifier.padding(top = Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) { actions() }
        }
    }
}
