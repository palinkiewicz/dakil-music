package pl.dakil.music.presentation.components

import java.util.Locale
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
