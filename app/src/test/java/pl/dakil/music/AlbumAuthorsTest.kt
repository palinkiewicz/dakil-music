package pl.dakil.music

import org.junit.Assert.assertEquals
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.util.AlbumAuthors
import org.junit.Test

class AlbumAuthorsTest {

    private val album = listOf(
        listOf("Alice", "Bob"),   // track 1
        listOf("Alice"),          // track 2
        listOf("Carol", "Alice"), // track 3
    )

    @Test
    fun firstSongArtists_returnsEveryArtistOfFirstSong() {
        assertEquals(listOf("Alice", "Bob"), AlbumAuthors.authorsOf(album, AlbumAuthorMode.FIRST_SONG_ARTISTS))
    }

    @Test
    fun firstArtistOfFirstSong_returnsOnlyTheFirst() {
        assertEquals(listOf("Alice"), AlbumAuthors.authorsOf(album, AlbumAuthorMode.FIRST_ARTIST_OF_FIRST_SONG))
    }

    @Test
    fun mostCommon_picksArtistOnMostTracks() {
        // Alice appears on all 3 tracks, Bob and Carol on 1 each.
        assertEquals(listOf("Alice"), AlbumAuthors.authorsOf(album, AlbumAuthorMode.MOST_COMMON))
    }

    @Test
    fun mostCommon_countsEachArtistOncePerTrack() {
        val dupes = listOf(
            listOf("Bob", "Bob"), // a duplicated artist on one song must not out-count others
            listOf("Alice"),
            listOf("Alice"),
        )
        assertEquals(listOf("Alice"), AlbumAuthors.authorsOf(dupes, AlbumAuthorMode.MOST_COMMON))
    }

    @Test
    fun allArtists_returnsDistinctInFirstAppearanceOrder() {
        assertEquals(listOf("Alice", "Bob", "Carol"), AlbumAuthors.authorsOf(album, AlbumAuthorMode.ALL_ARTISTS))
    }

    @Test
    fun emptyAlbum_returnsEmpty() {
        assertEquals(emptyList<String>(), AlbumAuthors.authorsOf(emptyList(), AlbumAuthorMode.ALL_ARTISTS))
    }
}
