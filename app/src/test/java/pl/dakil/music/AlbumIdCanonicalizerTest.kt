package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.domain.util.AlbumIdCanonicalizer
import pl.dakil.music.domain.util.AlbumIdCanonicalizer.Track

class AlbumIdCanonicalizerTest {

    private val clean = "Nic się nie stało"
    private val mangled = "Nic siÄ™ nie staĹ‚o"

    private fun track(
        albumId: Long,
        album: String,
        albumArtist: String = "",
        vararg artists: String,
    ) = Track(albumId, album, albumArtist, artists.toList())

    /**
     * The real split: one album indexed under two ids because the extractor mangled
     * some of its tracks. The intact id wins, since it is the one that survives if the
     * platform ever scans those files correctly.
     */
    @Test
    fun foldsMangledIdIntoCleanId() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(688978974787505853L, clean),
                track(688978974787505853L, clean),
                track(2092176436164639611L, mangled),
                track(2092176436164639611L, mangled),
            ),
        )
        assertEquals(mapOf(2092176436164639611L to 688978974787505853L), result)
    }

    @Test
    fun prefersCleanIdEvenWhenMangledTracksOutnumberIt() {
        val tracks = listOf(track(50L, clean)) + List(9) { track(10L, mangled) }
        assertEquals(mapOf(10L to 50L), AlbumIdCanonicalizer.canonicalIds(tracks))
    }

    /**
     * With no intact spelling to aim at, the lowest id keeps the result deterministic.
     * "Björk" mangles differently through windows-1250 and windows-1252, but both
     * repair back to the same title and so belong to one album.
     */
    @Test
    fun collapsesOntoLowestIdWhenEverySpellingIsMangled() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(30L, "BjÃ¶rk"),
                track(10L, "BjĂ¶rk"),
                track(20L, "BjÃ¶rk"),
            ),
        )
        assertEquals(mapOf(20L to 10L, 30L to 10L), result)
    }

    /**
     * The other real split, and the one that needs no mojibake at all: the flac of
     * "Your Lie In April" carries an album artist tag and its two mp3 siblings in the
     * same folder do not, so MediaStore keyed one by artist and the others by
     * directory. The tagged id wins — it is the one that survives the files moving.
     */
    @Test
    fun foldsUntaggedIdIntoTheAlbumArtistTaggedOne() {
        val album = "Your Lie In April Original Soundtrack Vol 1"
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(5443821081736555308L, album, artists = arrayOf("Masaru Yokoyama")),
                track(5443821081736555308L, album, artists = arrayOf("Masaru Yokoyama")),
                track(7065708633590584115L, album, "Masaru Yokoyama", "Masaru Yokoyama"),
            ),
        )
        assertEquals(mapOf(5443821081736555308L to 7065708633590584115L), result)
    }

    /** Both halves damaged and split by tagging at once: still one album. */
    @Test
    fun foldsAnIdSplitByBothMojibakeAndTagging() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(3L, mangled, artists = arrayOf("Kuqe 2115")),
                track(1L, clean, artists = arrayOf("Kuqe 2115")),
                track(2L, clean, "Kuqe 2115", "Kuqe 2115"),
            ),
        )
        assertEquals(mapOf(1L to 2L, 3L to 2L), result)
    }

    /**
     * The safety property: two artists' `Greatest Hits` are different albums, and no
     * amount of tagging asymmetry may fuse them.
     */
    @Test
    fun neverMergesSameTitleByDifferentArtists() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(1L, "Greatest Hits", artists = arrayOf("Queen")),
                track(2L, "Greatest Hits", "ABBA", "ABBA"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    /**
     * Two untagged ids sharing a title and an artist differ only by the directory
     * MediaStore fell back to — indistinguishable from two genuinely separate albums,
     * so they stay apart.
     */
    @Test
    fun neverMergesTwoUntaggedCleanIds() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(1L, "Live", artists = arrayOf("Queen")),
                track(2L, "Live", artists = arrayOf("Queen")),
            ),
        )
        assertTrue(result.isEmpty())
    }

    /**
     * A guest artist on one track must not give that album a different author and
     * strand it: the album is still Chivas', so its untagged half still finds the
     * tagged one.
     */
    @Test
    fun aCollabTrackDoesNotChangeTheAlbumsAuthor() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(11L, "DWADZIEŚCIASIEDEM.", artists = arrayOf("Chivas")),
                track(11L, "DWADZIEŚCIASIEDEM.", artists = arrayOf("Chivas", "White 2115")),
                track(11L, "DWADZIEŚCIASIEDEM.", artists = arrayOf("Chivas")),
                track(22L, "DWADZIEŚCIASIEDEM.", "Chivas", "Chivas", "Szpaku"),
            ),
        )
        assertEquals(mapOf(11L to 22L), result)
    }

    /** Ambiguous target — two clean candidates — so the mangled tracks stay put. */
    @Test
    fun leavesMangledIdAloneWhenTwoCleanIdsShareTheIdentity() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(1L, clean),
                track(2L, clean),
                track(3L, mangled),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun undamagedLibraryYieldsNoChanges() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(1L, "DWADZIEŚCIASIEDEM.", artists = arrayOf("Chivas")),
                track(1L, "DWADZIEŚCIASIEDEM.", artists = arrayOf("Chivas")),
                track(2L, "Król Lew", artists = arrayOf("Various")),
                track(3L, "Greatest Hits", "ABBA", "ABBA"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSongsWithoutAnAlbum() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(0L, ""),
                track(0L, "   "),
                track(1L, clean),
            ),
        )
        assertTrue(result.isEmpty())
    }

    /** Titles and authors are matched with the normalization the rest of the app uses. */
    @Test
    fun matchesIdentitiesIgnoringCaseAndWhitespace() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                track(7L, "  nic   SIĘ nie stało "),
                track(9L, mangled),
            ),
        )
        assertEquals(mapOf(9L to 7L), result)
    }

    @Test
    fun emptyInputYieldsEmptyMap() {
        assertTrue(AlbumIdCanonicalizer.canonicalIds(emptyList()).isEmpty())
    }
}
