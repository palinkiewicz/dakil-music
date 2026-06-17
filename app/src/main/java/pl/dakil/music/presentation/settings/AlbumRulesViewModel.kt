package pl.dakil.music.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.AlbumRule
import pl.dakil.music.domain.util.AlbumKey

/** A custom album rule paired with a friendly display title resolved from the library. */
data class AlbumRuleItem(val rule: AlbumRule, val title: String, val artist: String)

class AlbumRulesViewModel(private val container: AppContainer) : ViewModel() {

    val rules: StateFlow<List<AlbumRuleItem>> = combine(
        container.observeAlbumRules(),
        container.musicRepository.songs,
    ) { rules, songs ->
        // Resolve each rule's key back to an album title/artist for display.
        val infoByKey = songs.filter { it.album.isNotBlank() }
            .groupBy { it.albumId }
            .values
            .associate { group -> AlbumKey.of(group) to group }
        rules.map { rule ->
            val group = infoByKey[rule.albumKey]
            AlbumRuleItem(
                rule = rule,
                title = group?.firstOrNull()?.album ?: rule.albumKey,
                artist = group?.minWithOrNull(compareBy({ it.trackNumber }, { it.id }))?.rawArtist.orEmpty(),
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(albumKey: String) = viewModelScope.launch {
        container.deleteAlbumRule(albumKey)
    }
}
