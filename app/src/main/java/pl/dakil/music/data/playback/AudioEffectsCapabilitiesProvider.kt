package pl.dakil.music.data.playback

import android.content.Context
import android.media.AudioManager
import pl.dakil.music.domain.model.AudioEffectsCapabilities

/**
 * Reads the device's [AudioEffectsCapabilities] once, in the app process. Effect
 * capabilities (band count, frequencies, preset names) are device-constant, so we
 * inspect a throwaway effect on a generated session id instead of ferrying the data
 * across the playback-service boundary. The result is cached for the process lifetime.
 */
class AudioEffectsCapabilitiesProvider(context: Context) {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val capabilities: AudioEffectsCapabilities by lazy {
        val sessionId = runCatching { audioManager.generateAudioSessionId() }
            .getOrDefault(AudioManager.ERROR)
        if (sessionId == AudioManager.ERROR) return@lazy AudioEffectsCapabilities(available = false)
        val controller = AudioEffectsController(sessionId)
        try {
            controller.capabilities()
        } finally {
            controller.release()
        }
    }

    fun get(): AudioEffectsCapabilities = capabilities
}
