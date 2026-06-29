package pl.dakil.music.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AudioEffectsSettings

/** Persisted audio-effects state (DataStore-backed). All values have sensible defaults. */
interface AudioEffectsRepository {

    val settings: Flow<AudioEffectsSettings>

    suspend fun setMasterEnabled(enabled: Boolean)

    /** Select a device preset; pass [AudioEffectsSettings.PRESET_CUSTOM] to detach from presets. */
    suspend fun setPreset(preset: Int)

    /** Persist manual per-band gains (millibels) and switch to the custom preset. */
    suspend fun setBandLevels(levelsMb: List<Int>)

    suspend fun setBassBoostStrength(strength: Int)

    suspend fun setVirtualizerStrength(strength: Int)

    /** Zero the bands and detach from any preset, leaving the master switch untouched. */
    suspend fun resetToFlat()
}
