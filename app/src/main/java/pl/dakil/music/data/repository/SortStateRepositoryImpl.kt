package pl.dakil.music.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import pl.dakil.music.domain.repository.SortStateRepository
import pl.dakil.music.presentation.library.AlbumSortOption
import pl.dakil.music.presentation.library.ArtistSortOption
import pl.dakil.music.presentation.library.PlaylistSortOption
import pl.dakil.music.presentation.library.SortDirection
import pl.dakil.music.presentation.library.SortState

class SortStateRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SortStateRepository {

    override suspend fun saveAlbumSort(state: SortState<AlbumSortOption>) {
        dataStore.edit {
            it[KEY_ALBUM_OPTION] = state.option.name
            it[KEY_ALBUM_DESC] = state.direction == SortDirection.DESC
        }
    }

    override suspend fun saveArtistSort(state: SortState<ArtistSortOption>) {
        dataStore.edit {
            it[KEY_ARTIST_OPTION] = state.option.name
            it[KEY_ARTIST_DESC] = state.direction == SortDirection.DESC
        }
    }

    override suspend fun savePlaylistSort(state: SortState<PlaylistSortOption>) {
        dataStore.edit {
            it[KEY_PLAYLIST_OPTION] = state.option.name
            it[KEY_PLAYLIST_DESC] = state.direction == SortDirection.DESC
        }
    }

    override suspend fun loadAlbumSort(): SortState<AlbumSortOption> {
        val prefs = dataStore.data.first()
        val option = prefs[KEY_ALBUM_OPTION]?.let { name ->
            AlbumSortOption.entries.firstOrNull { it.name == name }
        } ?: AlbumSortOption.ALBUM_NAME
        val desc = prefs[KEY_ALBUM_DESC] ?: false
        return SortState(option, if (desc) SortDirection.DESC else SortDirection.ASC)
    }

    override suspend fun loadArtistSort(): SortState<ArtistSortOption> {
        val prefs = dataStore.data.first()
        val option = prefs[KEY_ARTIST_OPTION]?.let { name ->
            ArtistSortOption.entries.firstOrNull { it.name == name }
        } ?: ArtistSortOption.ARTIST_NAME
        val desc = prefs[KEY_ARTIST_DESC] ?: false
        return SortState(option, if (desc) SortDirection.DESC else SortDirection.ASC)
    }

    override suspend fun loadPlaylistSort(): SortState<PlaylistSortOption> {
        val prefs = dataStore.data.first()
        val option = prefs[KEY_PLAYLIST_OPTION]?.let { name ->
            PlaylistSortOption.entries.firstOrNull { it.name == name }
        } ?: PlaylistSortOption.PLAYLIST_NAME
        val desc = prefs[KEY_PLAYLIST_DESC] ?: false
        return SortState(option, if (desc) SortDirection.DESC else SortDirection.ASC)
    }

    private companion object {
        val KEY_ALBUM_OPTION = stringPreferencesKey("album_sort_option")
        val KEY_ALBUM_DESC = booleanPreferencesKey("album_sort_desc")
        val KEY_ARTIST_OPTION = stringPreferencesKey("artist_sort_option")
        val KEY_ARTIST_DESC = booleanPreferencesKey("artist_sort_desc")
        val KEY_PLAYLIST_OPTION = stringPreferencesKey("playlist_sort_option")
        val KEY_PLAYLIST_DESC = booleanPreferencesKey("playlist_sort_desc")
    }
}
