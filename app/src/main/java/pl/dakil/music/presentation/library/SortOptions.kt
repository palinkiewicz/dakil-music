package pl.dakil.music.presentation.library

import pl.dakil.music.R

enum class SortDirection { ASC, DESC }

enum class AlbumSortOption(val labelRes: Int) {
    ALBUM_NAME(R.string.sort_album_name),
    ARTIST_NAME(R.string.sort_artist_name),
    SONG_COUNT(R.string.sort_song_count),
    DURATION(R.string.sort_duration),
    RELEASE_YEAR(R.string.sort_release_year),
    LISTENING_DURATION(R.string.sort_listening_duration),
    TRACKS_PLAYED(R.string.sort_tracks_played),
}

enum class ArtistSortOption(val labelRes: Int) {
    ARTIST_NAME(R.string.sort_artist_name),
    SONG_COUNT(R.string.sort_song_count),
    LISTENING_DURATION(R.string.sort_listening_duration),
    TRACKS_PLAYED(R.string.sort_tracks_played),
}

enum class PlaylistSortOption(val labelRes: Int) {
    PLAYLIST_NAME(R.string.sort_playlist_name),
    SONG_COUNT(R.string.sort_song_count),
    DURATION(R.string.sort_duration),
}

data class SortState<T>(val option: T, val direction: SortDirection = SortDirection.ASC) {
    fun select(newOption: T): SortState<T> = if (newOption == option) {
        copy(direction = if (direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    } else {
        SortState(newOption, SortDirection.ASC)
    }
}
