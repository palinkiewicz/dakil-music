package pl.dakil.music.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.music.R
import pl.dakil.music.domain.model.SongFileInfo

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (multi) R.string.file_info_title_multi else R.string.file_info_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (multi) {
                    InfoRow(
                        stringResource(R.string.file_info_count),
                        infos.size.toString(),
                    )
                    InfoRow(
                        stringResource(R.string.file_info_total_size),
                        formatFileSize(infos.sumOf { it.sizeBytes }),
                    )
                    InfoRow(
                        stringResource(R.string.file_info_total_duration),
                        formatDuration(infos.sumOf { it.durationMs }),
                    )
                } else {
                    val info = infos.first()
                    val unknown = stringResource(R.string.file_info_unknown)
                    InfoRow(stringResource(R.string.file_info_path), info.path ?: unknown)
                    InfoRow(stringResource(R.string.file_info_size), formatFileSize(info.sizeBytes))
                    InfoRow(stringResource(R.string.file_info_duration), formatDuration(info.durationMs))
                    InfoRow(
                        stringResource(R.string.file_info_bitrate),
                        if (info.bitrateBps > 0) formatBitrate(info.bitrateBps) else unknown,
                    )
                    InfoRow(
                        stringResource(R.string.file_info_format),
                        info.format.ifBlank { unknown },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
