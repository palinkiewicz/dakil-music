package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.music.domain.model.AudioEffectsSettings
import pl.dakil.music.domain.repository.AudioEffectsRepository
import pl.dakil.music.domain.util.BandLevelCodec

class AudioEffectsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : AudioEffectsRepository {

    override val settings: Flow<AudioEffectsSettings> = dataStore.data.map { prefs ->
        AudioEffectsSettings(
            masterEnabled = prefs[KEY_MASTER_ENABLED] ?: false,
            preset = prefs[KEY_PRESET] ?: AudioEffectsSettings.PRESET_CUSTOM,
            bandLevelsMb = BandLevelCodec.parse(prefs[KEY_BAND_LEVELS]),
            bassBoostStrength = prefs[KEY_BASS_BOOST] ?: 0,
        )
    }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MASTER_ENABLED] = enabled }
    }

    override suspend fun setPreset(preset: Int) {
        dataStore.edit {
            it[KEY_PRESET] = preset
            // A device preset overrides any manual band levels.
            if (preset != AudioEffectsSettings.PRESET_CUSTOM) it.remove(KEY_BAND_LEVELS)
        }
    }

    override suspend fun setBandLevels(levelsMb: List<Int>) {
        dataStore.edit {
            it[KEY_BAND_LEVELS] = BandLevelCodec.serialize(levelsMb)
            it[KEY_PRESET] = AudioEffectsSettings.PRESET_CUSTOM
        }
    }

    override suspend fun setBassBoostStrength(strength: Int) {
        dataStore.edit { it[KEY_BASS_BOOST] = strength.coerceIn(0, AudioEffectsSettings.STRENGTH_MAX) }
    }

    override suspend fun resetToFlat() {
        dataStore.edit {
            it.remove(KEY_BAND_LEVELS)
            it[KEY_PRESET] = AudioEffectsSettings.PRESET_CUSTOM
            it[KEY_BASS_BOOST] = 0
        }
    }

    private companion object {
        val KEY_MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val KEY_PRESET = intPreferencesKey("preset")
        val KEY_BAND_LEVELS = stringPreferencesKey("band_levels_mb")
        val KEY_BASS_BOOST = intPreferencesKey("bass_boost_strength")
    }
}
