package pl.dakil.music.data.playback

import android.content.Context
import android.media.AudioManager
import pl.dakil.music.domain.model.AudioEffectsCapabilities

/**
 * Builds the [AudioEffectsCapabilities] once, in the app process. The band layout and
 * presets are app-defined ([EqualizerSpec]), so only availability is device-dependent —
 * probed by creating a throwaway effect on a generated session id. The result is cached
 * for the process lifetime.
 */
class AudioEffectsCapabilitiesProvider(context: Context) {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val capabilities: AudioEffectsCapabilities by lazy {
        val sessionId = runCatching { audioManager.generateAudioSessionId() }
            .getOrDefault(AudioManager.ERROR)
        if (sessionId == AudioManager.ERROR) return@lazy AudioEffectsCapabilities(available = false)
        val controller = AudioEffectsController(sessionId)
        val available = try {
            controller.isAvailable
        } finally {
            controller.release()
        }
        if (!available) return@lazy AudioEffectsCapabilities(available = false)
        AudioEffectsCapabilities(
            available = true,
            numberOfBands = EqualizerSpec.BAND_COUNT,
            minLevelMb = EqualizerSpec.MIN_LEVEL_MB,
            maxLevelMb = EqualizerSpec.MAX_LEVEL_MB,
            centerFreqsMilliHz = EqualizerSpec.CENTER_FREQS_HZ.map { it * 1000 },
            presetNames = EqualizerSpec.PRESETS.map { appContext.getString(it.nameRes) },
            presetBandLevelsMb = EqualizerSpec.PRESETS.map { it.levelsMb },
            bassBoostSupported = true,
        )
    }

    fun get(): AudioEffectsCapabilities = capabilities
}
