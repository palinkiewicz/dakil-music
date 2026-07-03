package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.data.mediastore.UriAudioResolver

class UriAudioResolverTest {

    @Test
    fun syntheticId_isAlwaysNegative() {
        val uris = listOf(
            "content://com.android.providers.downloads.documents/document/123",
            "content://media/external/audio/media/42",
            "file:///sdcard/Recordings/voice%20note.m4a",
            "",
        )
        uris.forEach { uri ->
            assertTrue(
                "id for $uri must be negative",
                UriAudioResolver.syntheticIdFor(uri) < 0,
            )
        }
    }

    @Test
    fun syntheticId_isDeterministic() {
        val uri = "content://com.example.files/audio/note.opus"
        assertEquals(
            UriAudioResolver.syntheticIdFor(uri),
            UriAudioResolver.syntheticIdFor(uri),
        )
    }

    @Test
    fun syntheticId_differsForDifferentUris() {
        assertNotEquals(
            UriAudioResolver.syntheticIdFor("content://a/1"),
            UriAudioResolver.syntheticIdFor("content://a/2"),
        )
    }

    @Test
    fun syntheticId_roundTripsThroughMediaIdString() {
        // MediaItemMapper uses song.id.toString() as the mediaId and requires
        // mediaId.toLongOrNull() on the way back — a synthetic id must survive that.
        val id = UriAudioResolver.syntheticIdFor("content://com.example/x.mp3")
        assertEquals(id, id.toString().toLongOrNull())
    }
}
