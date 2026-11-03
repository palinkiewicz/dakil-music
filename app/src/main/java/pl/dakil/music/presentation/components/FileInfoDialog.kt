package pl.dakil.music.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import pl.dakil.music.R
import pl.dakil.music.domain.model.SongFileInfo
import pl.dakil.music.ui.designsystem.FormSheet
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing
import pl.dakil.music.ui.theme.tabular

/**
 * Shows filesystem details for the selected song(s). A single selection lists the file
 * path, size, duration, bitrate and format; a multi-selection summarizes the count and
 * the cumulative size and duration.
 */
@Composable
fun FileInfoDialog(
    infos: List<SongFileInfo>,
    onDismiss: () -> Unit,
) {
    val multi = infos.size > 1
    val unknown = stringResource(R.string.file_info_unknown)
    val rows: List<Pair<String, String>> = if (multi) {
        listOf(
            stringResource(R.string.file_info_count) to infos.size.toString(),
            stringResource(R.string.file_info_total_size) to formatFileSize(infos.sumOf { it.sizeBytes }),
            stringResource(R.string.file_info_total_duration) to formatDuration(infos.sumOf { it.durationMs }),
        )
    } else {
        val info = infos.first()
        listOf(
            stringResource(R.string.file_info_format) to info.format.ifBlank { unknown },
            stringResource(R.string.file_info_size) to formatFileSize(info.sizeBytes),
            stringResource(R.string.file_info_duration) to formatDuration(info.durationMs),
            stringResource(R.string.file_info_bitrate) to if (info.bitrateBps > 0) formatBitrate(info.bitrateBps) else unknown,
        )
    }

    FormSheet(
        title = stringResource(if (multi) R.string.file_info_title_multi else R.string.file_info_title),
        symbol = AppSymbols.AudioFile,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.action_close),
    ) {
        // The facts as one card of label/value rows; the path, often long, gets its own block.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                rows.forEach { (label, value) -> InfoRow(label, value) }
            }
        }
        if (!multi) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    Text(
                        stringResource(R.string.file_info_path),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(infos.first().path ?: unknown, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMediumEmphasized.tabular(),
            textAlign = TextAlign.End,
        )
    }
}
