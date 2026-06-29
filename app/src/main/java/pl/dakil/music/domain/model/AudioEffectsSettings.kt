package pl.dakil.music.domain.model

/**
 * User-controlled audio-effects state, persisted in the `audio_effects` DataStore.
 * Both the UI process and the playback service observe this as the single source of
 * truth — the service applies it to the platform [android.media.audiofx] effects.
 */
data class AudioEffectsSettings(
    /** Master switch; when false all effects are bypassed regardless of the values below. */
    val masterEnabled: Boolean = false,
    /** Active device preset index, or [PRESET_CUSTOM] when the bands were set manually. */
    val preset: Int = PRESET_CUSTOM,
    /** Per-band gains in millibels; empty means flat / preset-driven. */
    val bandLevelsMb: List<Int> = emptyList(),
    /** Bass boost strength on the Android 0..1000 scale. */
    val bassBoostStrength: Int = 0,
    /** Virtualizer strength on the Android 0..1000 scale. */
    val virtualizerStrength: Int = 0,
) {
    companion object {
        /** [preset] sentinel meaning "no device preset — use [bandLevelsMb]". */
        const val PRESET_CUSTOM = -1

        /** Upper bound of the Android effect-strength scale (per the platform API). */
        const val STRENGTH_MAX = 1000
    }
}

/**
 * Static, device-determined description of the available effects. Read once in the
 * app process (effects are device-constant) rather than ferried across the service
 * boundary. When [available] is false the device exposes no usable equalizer.
 */
data class AudioEffectsCapabilities(
    val available: Boolean = false,
    val numberOfBands: Int = 0,
    val minLevelMb: Int = 0,
    val maxLevelMb: Int = 0,
    /** Center frequency of each band, in milli-hertz. Size == [numberOfBands]. */
    val centerFreqsMilliHz: List<Int> = emptyList(),
    /** Device preset names, indexed by preset id. */
    val presetNames: List<String> = emptyList(),
    /** Per-band gains (millibels) for each preset, indexed by preset id; lets the UI
     * seed the manual sliders when the user starts editing a preset. */
    val presetBandLevelsMb: List<List<Int>> = emptyList(),
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
)
