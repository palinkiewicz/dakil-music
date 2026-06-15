package pl.dakil.music.domain.usecase

import pl.dakil.music.domain.model.Album
import pl.dakil.music.domain.model.Performer
import pl.dakil.music.domain.model.Playlist
import pl.dakil.music.domain.model.SearchResults
import pl.dakil.music.domain.model.Song

class SearchLibraryUseCase {

    operator fun invoke(
        rawQuery: String,
        songs: List<Song>,
        albums: List<Album>,
        performers: List<Performer>,
        playlists: List<Playlist>,
        playlistDisplayName: (Playlist) -> String,
    ): SearchResults {
        val query = rawQuery.trim().lowercase()
        if (query.isBlank()) return SearchResults()

        return SearchResults(
            songs = songs.filter { matchesSong(it, query) },
            albums = albums.filter { matchesAlbum(it, query) },
            artists = performers.filter { it.name.lowercase().contains(query) },
            playlists = playlists.filter { playlistDisplayName(it).lowercase().contains(query) },
        )
    }

    private fun matchesSong(song: Song, query: String): Boolean =
        song.title.lowercase().contains(query) ||
            song.artists.any { it.lowercase().contains(query) } ||
            song.rawArtist.lowercase().contains(query) ||
            song.album.lowercase().contains(query) ||
            song.genre.lowercase().contains(query) ||
            (song.year > 0 && song.year.toString().contains(query))

    private fun matchesAlbum(album: Album, query: String): Boolean =
        album.title.lowercase().contains(query) ||
            album.artist.lowercase().contains(query)
}
