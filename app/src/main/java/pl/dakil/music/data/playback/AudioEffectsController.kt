package pl.dakil.music.data.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import pl.dakil.music.domain.model.AudioEffectsCapabilities
import pl.dakil.music.domain.model.AudioEffectsSettings

/**
 * Owns the platform [Equalizer]/[BassBoost]/[Virtualizer] for a single audio session
 * and applies [AudioEffectsSettings] to them. Lives in the playback-service process,
 * which is where the ExoPlayer audio session exists.
 *
 * Every platform call is guarded: audio-effect creation throws on some devices and
 * emulators, in which case the controller degrades gracefully to "unavailable".
 */
class AudioEffectsController(audioSessionId: Int) {

    private val equalizer: Equalizer? = runCatching {
        // Priority 0, attached to the player's session.
        Equalizer(0, audioSessionId)
    }.onFailure { Log.w(TAG, "Equalizer unavailable", it) }.getOrNull()

    private val bassBoost: BassBoost? = runCatching {
        BassBoost(0, audioSessionId)
    }.onFailure { Log.w(TAG, "BassBoost unavailable", it) }.getOrNull()

    private val virtualizer: Virtualizer? = runCatching {
        Virtualizer(0, audioSessionId)
    }.onFailure { Log.w(TAG, "Virtualizer unavailable", it) }.getOrNull()

    fun capabilities(): AudioEffectsCapabilities {
        val eq = equalizer ?: return AudioEffectsCapabilities(available = false)
        return runCatching {
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange // [min, max] in millibels
            val presetCount = eq.numberOfPresets.toInt()
            val presetNames = (0 until presetCount).map { eq.getPresetName(it.toShort()) }
            // Read each preset's band levels so the UI can seed the manual sliders.
            val presetLevels = (0 until presetCount).map { preset ->
                eq.usePreset(preset.toShort())
                (0 until bandCount).map { eq.getBandLevel(it.toShort()).toInt() }
            }
            AudioEffectsCapabilities(
                available = bandCount > 0,
                numberOfBands = bandCount,
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                centerFreqsMilliHz = (0 until bandCount).map { eq.getCenterFreq(it.toShort()) },
                presetNames = presetNames,
                presetBandLevelsMb = presetLevels,
                bassBoostSupported = bassBoost?.strengthSupported == true,
                virtualizerSupported = virtualizer?.strengthSupported == true,
            )
        }.onFailure { Log.w(TAG, "Failed to read capabilities", it) }
            .getOrDefault(AudioEffectsCapabilities(available = false))
    }

    /** Apply the desired state to the live effects. Safe to call repeatedly. */
    fun apply(settings: AudioEffectsSettings) {
        val on = settings.masterEnabled
        applyEqualizer(settings, on)
        bassBoost?.let { applyBassBoost(it, on, settings.bassBoostStrength) }
        virtualizer?.let { applyVirtualizer(it, on, settings.virtualizerStrength) }
    }

    private fun applyEqualizer(settings: AudioEffectsSettings, on: Boolean) {
        val eq = equalizer ?: return
        runCatching {
            val bandCount = eq.numberOfBands.toInt()
            val levels = settings.bandLevelsMb
            val usingPreset = settings.preset != AudioEffectsSettings.PRESET_CUSTOM &&
                settings.preset < eq.numberOfPresets
            val hasManualBoost = levels.size == bandCount && levels.any { it != 0 }
            // A flat equalizer is left out of the effect chain entirely: an active but
            // flat EQ needlessly interacts with BassBoost/Virtualizer on some devices.
            val active = on && (usingPreset || hasManualBoost)

            // Enable first, then write parameters — the order the AudioEffect API expects.
            // Writing band levels to a disabled effect and enabling afterwards mutes
            // output on some devices.
            eq.enabled = active
            if (!active) return
            if (usingPreset) {
                eq.usePreset(settings.preset.toShort())
            } else {
                val range = eq.bandLevelRange
                for (band in 0 until bandCount) {
                    val clamped = levels[band].coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), clamped.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "Failed to apply equalizer", it) }
    }

    private fun applyBassBoost(effect: BassBoost, masterOn: Boolean, strength: Int) {
        runCatching {
            val active = masterOn && strength > 0
            effect.enabled = active
            if (active && effect.strengthSupported) {
                effect.setStrength(strength.coerceIn(0, MAX_STRENGTH).toShort())
            }
        }.onFailure { Log.w(TAG, "Failed to apply BassBoost", it) }
    }

    private fun applyVirtualizer(effect: Virtualizer, masterOn: Boolean, strength: Int) {
        runCatching {
            val active = masterOn && strength > 0
            effect.enabled = active
            if (active && effect.strengthSupported) {
                effect.setStrength(strength.coerceIn(0, MAX_STRENGTH).toShort())
                // Force a virtualization mode while active; left on AUTO the effect stays
                // inaudible for ordinary stereo output on most devices.
                val forced = effect.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                if (!forced) effect.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO)
            }
        }.onFailure { Log.w(TAG, "Failed to apply Virtualizer", it) }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
    }

    private companion object {
        const val TAG = "AudioEffectsController"
        const val MAX_STRENGTH = 1000
    }
}
