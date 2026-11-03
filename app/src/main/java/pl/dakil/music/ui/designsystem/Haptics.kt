package pl.dakil.music.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's haptic vocabulary, so every screen "feels" the same for the same kind of action.
 * Always go through this (never `View.performHapticFeedback`): the theme swaps
 * [LocalHapticFeedback] for a no-op when the user turns haptics off.
 */
@Stable
class AppHaptics(private val feedback: HapticFeedback) {
    /** A meaningful action landed (deleted, copied, shared). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** The action isn't allowed. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** Moving between discrete positions (tabs, segmented options, slider steps). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A light tick for rapid, repeated steps (drag reorder). */
    fun frequentTick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** A drag crossed a threshold that changes what releasing would do (over the trash). */
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AppHaptics(feedback) }
}
