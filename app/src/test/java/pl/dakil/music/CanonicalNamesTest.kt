package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.music.domain.util.CanonicalNames

class CanonicalNamesTest {

    /**
     * The reported bug: one artist credited both ways listed as two performers, each
     * with a count that disagreed with the songs its screen opened.
     */
    @Test
    fun mergesSpellingsThatDifferOnlyInCase() {
        val counts = CanonicalNames.counts(
            listOf(
                "27.Fuckdemons",
                "27.FUCKDEMONS",
                "27.Fuckdemons",
                "27.FUCKDEMONS",
                "27.Fuckdemons",
            ),
        )
        assertEquals(mapOf("27.Fuckdemons" to 5), counts)
    }

    /** The whole group's size is reported, not just the winning spelling's. */
    @Test
    fun countsEveryOccurrenceInTheGroup() {
        val counts = CanonicalNames.counts(List(9) { if (it < 5) "Szpaku" else "SZPAKU" })
        assertEquals(9, counts.getValue("Szpaku"))
    }

    /** An occasional shouty credit must not rename the artist. */
    @Test
    fun displaysTheMostUsedSpelling() {
        val counts = CanonicalNames.counts(listOf("KIZO", "Kizo", "Kizo", "Kizo"))
        assertEquals(setOf("Kizo"), counts.keys)
    }

    /** Deterministic when no spelling is more common than another. */
    @Test
    fun breaksFrequencyTiesAlphabetically() {
        assertEquals(setOf("KIZO"), CanonicalNames.counts(listOf("Kizo", "KIZO")).keys)
        assertEquals(setOf("KIZO"), CanonicalNames.counts(listOf("KIZO", "Kizo")).keys)
    }

    @Test
    fun keepsGenuinelyDifferentNamesApart() {
        val counts = CanonicalNames.counts(listOf("Chivas", "Szpaku", "Chivas"))
        assertEquals(mapOf("Chivas" to 2, "Szpaku" to 1), counts)
    }

    /** Case folding must not reach past ASCII incorrectly for Polish names. */
    @Test
    fun mergesAccentedSpellingsByCaseOnly() {
        val counts = CanonicalNames.counts(listOf("Kaz Bałagane", "KAZ BAŁAGANE", "Kaz Bałagane"))
        assertEquals(mapOf("Kaz Bałagane" to 3), counts)
        // Different letters, not different case — these stay separate.
        assertEquals(2, CanonicalNames.counts(listOf("Bałagane", "Balagane")).size)
    }

    @Test
    fun skipsBlankNames() {
        val counts = CanonicalNames.counts(listOf("", "   ", "Chivas"))
        assertEquals(mapOf("Chivas" to 1), counts)
    }

    @Test
    fun emptyInputYieldsEmptyResult() {
        assertTrue(CanonicalNames.counts(emptyList()).isEmpty())
    }
}
