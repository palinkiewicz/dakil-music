package pl.dakil.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.domain.model.AlbumAuthorMode
import pl.dakil.music.domain.model.AlbumCoverArtMode
import pl.dakil.music.domain.model.AlbumRule

/**
 * [AlbumRule.isEmpty] decides whether a rule is stored or deleted, so every override
 * has to count towards it — a "treated as No album" rule that reported itself empty
 * would be dropped on save and the album would spring back.
 */
class AlbumRuleTest {

    @Test
    fun ruleWithNoOverridesIsEmpty() {
        assertTrue(AlbumRule("album|artist").isEmpty)
    }

    @Test
    fun treatAsNoAlbumMakesARuleWorthKeeping() {
        assertFalse(AlbumRule("album|artist", treatAsNoAlbum = true).isEmpty)
    }

    @Test
    fun coverArtOrAuthorOverrideMakesARuleWorthKeeping() {
        assertFalse(AlbumRule("k", coverArtMode = AlbumCoverArtMode.INDIVIDUAL).isEmpty)
        assertFalse(AlbumRule("k", authorMode = AlbumAuthorMode.MOST_COMMON).isEmpty)
    }

    /** Clearing the flag on an otherwise bare rule must make it deletable again. */
    @Test
    fun clearingTreatAsNoAlbumEmptiesTheRule() {
        val rule = AlbumRule("k", treatAsNoAlbum = true)
        assertTrue(rule.copy(treatAsNoAlbum = false).isEmpty)
    }
}
