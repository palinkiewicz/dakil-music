package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.domain.util.AlbumIdCanonicalizer

class AlbumIdCanonicalizerTest {

    private val clean = "Nic się nie stało"
    private val mangled = "Nic siÄ™ nie staĹ‚o"

    /**
     * The real split: one album indexed under two ids because the extractor mangled
     * some of its tracks. The intact id wins, since it is the one that survives if the
     * platform ever scans those files correctly.
     */
    @Test
    fun foldsMangledIdIntoCleanId() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                688978974787505853L to clean,
                688978974787505853L to clean,
                2092176436164639611L to mangled,
                2092176436164639611L to mangled,
            ),
        )
        assertEquals(mapOf(2092176436164639611L to 688978974787505853L), result)
    }

    @Test
    fun prefersCleanIdEvenWhenMangledTracksOutnumberIt() {
        val songAlbums = listOf(50L to clean) + List(9) { 10L to mangled }
        assertEquals(mapOf(10L to 50L), AlbumIdCanonicalizer.canonicalIds(songAlbums))
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
                30L to "BjÃ¶rk",
                10L to "BjĂ¶rk",
                20L to "BjÃ¶rk",
            ),
        )
        assertEquals(mapOf(20L to 10L, 30L to 10L), result)
    }

    /**
     * The safety property: two undamaged ids sharing a title are a distinction the
     * device is making deliberately (album artist, most likely) and must survive.
     */
    @Test
    fun neverMergesTwoCleanIds() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                1L to "Greatest Hits",
                2L to "Greatest Hits",
            ),
        )
        assertTrue(result.isEmpty())
    }

    /** Ambiguous target — two clean candidates — so the mangled tracks stay put. */
    @Test
    fun leavesMangledIdAloneWhenTwoCleanIdsShareTheTitle() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                1L to clean,
                2L to clean,
                3L to mangled,
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun undamagedLibraryYieldsNoChanges() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                1L to "DWADZIEŚCIASIEDEM.",
                1L to "DWADZIEŚCIASIEDEM.",
                2L to "Król Lew",
                3L to "Greatest Hits",
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSongsWithoutAnAlbum() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                0L to "",
                0L to "   ",
                1L to clean,
            ),
        )
        assertTrue(result.isEmpty())
    }

    /** Titles are matched with the same normalization the rest of the app uses. */
    @Test
    fun matchesTitlesIgnoringCaseAndWhitespace() {
        val result = AlbumIdCanonicalizer.canonicalIds(
            listOf(
                7L to "  nic   SIĘ nie stało ",
                9L to mangled,
            ),
        )
        assertEquals(mapOf(9L to 7L), result)
    }

    @Test
    fun emptyInputYieldsEmptyMap() {
        assertTrue(AlbumIdCanonicalizer.canonicalIds(emptyList()).isEmpty())
    }
}
