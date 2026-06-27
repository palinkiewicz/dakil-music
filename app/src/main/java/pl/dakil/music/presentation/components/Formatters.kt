package pl.dakil.music.presentation.components

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

/** Formats a millisecond duration as m:ss (or h:mm:ss for long tracks). */
fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val total = millis.milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % 60
    val seconds = total.inWholeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** Formats a byte count using binary units (B, KB, MB, …). */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(group)
    val pattern = if (group == 0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.getDefault(), pattern, value, units[group])
}

/** Formats a bitrate in bits per second as kbps. */
fun formatBitrate(bitsPerSecond: Int): String =
    String.format(Locale.getDefault(), "%d kbps", bitsPerSecond / 1000)
