package pl.dakil.music.domain.repository

import pl.dakil.music.presentation.library.AlbumSortOption
import pl.dakil.music.presentation.library.ArtistSortOption
import pl.dakil.music.presentation.library.PlaylistSortOption
import pl.dakil.music.presentation.library.SortDirection
import pl.dakil.music.presentation.library.SortState

interface SortStateRepository {
    suspend fun saveAlbumSort(state: SortState<AlbumSortOption>)
    suspend fun saveArtistSort(state: SortState<ArtistSortOption>)
    suspend fun savePlaylistSort(state: SortState<PlaylistSortOption>)

    suspend fun loadAlbumSort(): SortState<AlbumSortOption>
    suspend fun loadArtistSort(): SortState<ArtistSortOption>
    suspend fun loadPlaylistSort(): SortState<PlaylistSortOption>
}
