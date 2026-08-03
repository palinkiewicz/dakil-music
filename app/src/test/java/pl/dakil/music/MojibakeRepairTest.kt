package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.domain.util.MojibakeRepair

class MojibakeRepairTest {

    // The two strings the platform scanner actually mangled on a pl-PL device, taken
    // verbatim from its MediaStore index. Both files hold correct UTF-8 on disk.
    @Test
    fun repairs_realWorldPolishAlbum() {
        assertEquals(
            "Nic się nie stało",
            MojibakeRepair.repair("Nic siÄ™ nie staĹ‚o"),
        )
    }

    @Test
    fun repairs_realWorldPolishTitle() {
        assertEquals(
            "Nigdzie się nie wybieram (przysięgam)",
            MojibakeRepair.repair("Nigdzie siÄ™ nie wybieram (przysiÄ™gam)"),
        )
    }

    @Test
    fun repairs_westernEuropeanMojibake() {
        assertEquals("Björk", MojibakeRepair.repair("BjÃ¶rk"))
        assertEquals("Beyoncé", MojibakeRepair.repair("BeyoncÃ©"))
    }

    @Test
    fun repairs_multiByteBeyondBmp() {
        // U+1F3B5 MUSICAL NOTE — a four-byte sequence, mangled through windows-1252.
        assertEquals("🎵", MojibakeRepair.repair("ðŸŽµ"))
    }

    /**
     * The important half: text that merely *contains* non-ASCII must survive
     * untouched. These are correctly-scanned strings from the same real library.
     */
    @Test
    fun leaves_correctlyEncodedTextUnchanged() {
        val untouched = listOf(
            "DWADZIEŚCIASIEDEM.",
            "Król Lew",
            "Żółwie Ninja",
            "Anioł Stróż",
            "Przyjdzie taki dzień gdy będę wolny",
            "Dom Dla Zmyślonych Przyjaciół Pana Mateusza",
            "nóż motylkowy",
            "ÖTZI",
            "Björk",
            "Москва",
        )
        for (text in untouched) {
            assertEquals(text, MojibakeRepair.repair(text))
        }
    }

    @Test
    fun leaves_asciiAndEmptyUnchanged() {
        assertEquals("", MojibakeRepair.repair(""))
        assertEquals("ROZPACZ", MojibakeRepair.repair("ROZPACZ"))
        assertEquals("27.Fuckdemons", MojibakeRepair.repair("27.Fuckdemons"))
    }

    /**
     * A codepage that cannot represent every character of the input cannot have
     * produced it. `Ĺ` (windows-1250 only) rules windows-1252 out, and the
     * windows-1250 reading is the one that decodes as UTF-8.
     */
    @Test
    fun picks_theCodepageThatFullyEncodesTheInput() {
        assertEquals("stało", MojibakeRepair.repair("staĹ‚o"))
    }

    /**
     * Strict decoding is what prevents false positives: a lenient decoder would
     * substitute U+FFFD and "repair" these into garbage.
     */
    @Test
    fun rejects_invalidUtf8ByteSequences() {
        // Lone continuation byte (windows-1252 ‚ = 0x82).
        assertEquals("a‚b", MojibakeRepair.repair("a‚b"))
        // Truncated two-byte sequence: lead byte 0xC4 with nothing after it.
        assertEquals("abcÄ", MojibakeRepair.repair("abcÄ"))
        // Overlong encoding of '/' (0xC0 0xAF).
        assertEquals("À¯", MojibakeRepair.repair("À¯"))
        // Encoded surrogate half U+D800 (0xED 0xA0 0x80) — not valid UTF-8.
        assertEquals("\u00ED\u00A0\u20AC", MojibakeRepair.repair("\u00ED\u00A0\u20AC"))
    }

    /** Bytes that decode to control characters are a wrong guess, not a repair. */
    @Test
    fun rejects_decodingsContainingControlCharacters() {
        // windows-1252 0xC2 0x80 decodes as U+0080, a C1 control.
        assertEquals("Â€", MojibakeRepair.repair("Â€"))
    }

    /** Repairing an already-repaired string must be a no-op. */
    @Test
    fun isIdempotent() {
        val once = MojibakeRepair.repair("Nic siÄ™ nie staĹ‚o")
        assertEquals(once, MojibakeRepair.repair(once))
    }
}
