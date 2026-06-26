package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.music.domain.model.LrclibMatch
import pl.dakil.music.domain.model.LyricsSource
import pl.dakil.music.domain.usecase.GetLyricsForSongUseCase

class GetLyricsForSongTest {

    private fun match(id: Long, duration: Double, synced: String? = null, plain: String? = "x") =
        LrclibMatch(
            id = id,
            trackName = "t$id",
            artistName = "a",
            albumName = "al",
            durationSec = duration,
            plainLyrics = plain,
            syncedLyrics = synced,
        )

    @Test
    fun `picks closest duration among matches with lyrics`() {
        val matches = listOf(
            match(1, 100.0),
            match(2, 133.0),
            match(3, 200.0),
        )
        val best = GetLyricsForSongUseCase.pickBest(matches, durationMs = 130_000L)
        assertEquals(2L, best?.id)
    }

    @Test
    fun `ignores matches without any lyrics`() {
        val matches = listOf(
            match(1, 133.0, synced = null, plain = null),
            match(2, 999.0, plain = "y"),
        )
        val best = GetLyricsForSongUseCase.pickBest(matches, durationMs = 133_000L)
        assertEquals(2L, best?.id)
    }

    @Test
    fun `returns null when no match has lyrics`() {
        val matches = listOf(match(1, 100.0, synced = null, plain = null))
        assertNull(GetLyricsForSongUseCase.pickBest(matches, 100_000L))
    }

    @Test
    fun `prefers synced lyrics when building from a match`() {
        val lyrics = GetLyricsForSongUseCase.lyricsFrom(
            match(1, 100.0, synced = "[00:01.00]hi", plain = "hi"),
        )
        assertEquals(LyricsSource.LRCLIB, lyrics.source)
        assertEquals(true, lyrics.synced)
    }
}
