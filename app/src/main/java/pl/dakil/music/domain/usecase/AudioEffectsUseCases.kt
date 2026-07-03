package pl.dakil.music.domain.usecase

import kotlinx.coroutines.flow.Flow
import pl.dakil.music.domain.model.AudioEffectsSettings
import pl.dakil.music.domain.repository.AudioEffectsRepository

class ObserveAudioEffectsUseCase(private val repository: AudioEffectsRepository) {
    operator fun invoke(): Flow<AudioEffectsSettings> = repository.settings
}

class UpdateAudioEffectsUseCase(private val repository: AudioEffectsRepository) {
    suspend fun setMasterEnabled(enabled: Boolean) = repository.setMasterEnabled(enabled)
    suspend fun setPreset(preset: Int) = repository.setPreset(preset)
    suspend fun setBandLevels(levelsMb: List<Int>) = repository.setBandLevels(levelsMb)
    suspend fun setBassBoostStrength(strength: Int) = repository.setBassBoostStrength(strength)
    suspend fun resetToFlat() = repository.resetToFlat()
}
