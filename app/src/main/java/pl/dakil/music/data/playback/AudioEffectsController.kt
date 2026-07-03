package pl.dakil.music.data.playback

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import pl.dakil.music.domain.model.AudioEffectsSettings

/**
 * Owns a platform [DynamicsProcessing] effect for a single audio session and applies
 * [AudioEffectsSettings] to it. Lives in the playback-service process, which is where
 * the ExoPlayer audio session exists.
 *
 * DynamicsProcessing replaces the legacy Equalizer + BassBoost pair: the old LVM effect
 * bundle silently attenuates the whole session to reserve clipping headroom whenever any
 * band is boosted, making everything sound quieter. Here we do our own gain staging —
 * the equalizer and the bass boost are both gains on a post-EQ stage (bass boost is a
 * low-shelf contribution on the bottom bands), guarded by an explicit limiter instead
 * of a blanket volume drop.
 *
 * Every platform call is guarded: audio-effect creation throws on some devices and
 * emulators, in which case the controller degrades gracefully to "unavailable".
 */
class AudioEffectsController(audioSessionId: Int) {

    init {
        Log.d(TAG, "Creating effects for audioSessionId=$audioSessionId")
    }

    private val dynamicsProcessing: DynamicsProcessing? = runCatching {
        // Priority 0, attached to the player's session.
        DynamicsProcessing(0, audioSessionId, buildConfig()).apply {
            setLimiterAllChannelsTo(buildLimiter())
        }
    }.onFailure { Log.w(TAG, "DynamicsProcessing unavailable", it) }.getOrNull()
        .also { Log.d(TAG, "DynamicsProcessing created=${it != null} hasControl=${it?.hasControl()}") }

    val isAvailable: Boolean get() = dynamicsProcessing != null

    /** Apply the desired state to the live effect. Safe to call repeatedly. */
    fun apply(settings: AudioEffectsSettings) {
        val dp = dynamicsProcessing ?: return
        runCatching {
            val levels = resolveBandLevelsMb(settings)
            val bassBoostDb = settings.bassBoostStrength
                .coerceIn(0, AudioEffectsSettings.STRENGTH_MAX)
                .toFloat() * BASS_BOOST_MAX_DB / AudioEffectsSettings.STRENGTH_MAX
            // A flat chain is left out of the effect path entirely — no reason to spend
            // DSP cycles (or risk device quirks) on an effect that changes nothing.
            val active = settings.masterEnabled && (levels.any { it != 0 } || bassBoostDb > 0f)

            // Enable first, then write parameters — the order the AudioEffect API expects.
            val status = dp.setEnabled(active)
            Log.d(TAG, "DynamicsProcessing setEnabled($active) -> $status; enabled=${dp.enabled} hasControl=${dp.hasControl()}")
            if (!active) return
            for (band in 0 until EqualizerSpec.BAND_COUNT) {
                val gainDb = levels[band] / 100f + bassBoostDb * BASS_SHELF_WEIGHTS[band]
                dp.setPostEqBandAllChannelsTo(
                    band,
                    DynamicsProcessing.EqBand(true, EqualizerSpec.CUTOFF_FREQS_HZ[band], gainDb),
                )
            }
        }.onFailure { Log.w(TAG, "Failed to apply audio effects", it) }
    }

    /** Band gains from the active preset, or the (clamped) manual levels, or flat. */
    private fun resolveBandLevelsMb(settings: AudioEffectsSettings): List<Int> {
        EqualizerSpec.PRESETS.getOrNull(settings.preset)?.let { return it.levelsMb }
        if (settings.bandLevelsMb.size != EqualizerSpec.BAND_COUNT) {
            return List(EqualizerSpec.BAND_COUNT) { 0 }
        }
        return settings.bandLevelsMb.map {
            it.coerceIn(EqualizerSpec.MIN_LEVEL_MB, EqualizerSpec.MAX_LEVEL_MB)
        }
    }

    private fun buildConfig(): DynamicsProcessing.Config =
        DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            // Channel count is a template; the platform re-fits the config to the
            // session's actual channel count on creation.
            /* channelCount = */ 2,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ false, /* mbcBandCount = */ 0,
            /* postEqInUse = */ true, /* postEqBandCount = */ EqualizerSpec.BAND_COUNT,
            /* limiterInUse = */ true,
        ).build()

    /**
     * Brick-wall-ish limiter that absorbs the headroom the band boosts eat into,
     * instead of the legacy bundle's fixed whole-session attenuation.
     */
    private fun buildLimiter() = DynamicsProcessing.Limiter(
        /* inUse = */ true,
        /* enabled = */ true,
        /* linkGroup = */ 0,
        /* attackTime = */ 1f,
        /* releaseTime = */ 60f,
        /* ratio = */ 10f,
        /* threshold = */ -1f,
        /* postGain = */ 0f,
    )

    fun release() {
        runCatching { dynamicsProcessing?.release() }
    }

    private companion object {
        const val TAG = "AudioEffectsController"

        /** Bass-boost gain in dB at full strength. */
        const val BASS_BOOST_MAX_DB = 6f

        /** How much of the bass boost each band receives — a low shelf over the bottom bands. */
        val BASS_SHELF_WEIGHTS = listOf(1f, 0.5f, 0f, 0f, 0f)
    }
}
