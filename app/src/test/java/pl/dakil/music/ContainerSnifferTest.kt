package pl.dakil.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.dakil.music.data.repository.ContainerSniffer

class ContainerSnifferTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun sniff(vararg parts: ByteArray): String? {
        val file = folder.newFile()
        file.writeBytes(parts.reduce(ByteArray::plus))
        return ContainerSniffer.extensionOf(file)
    }

    private fun magic(text: String) = text.toByteArray(Charsets.US_ASCII)

    private fun padding(count: Int) = ByteArray(count)

    /** An ID3v2 header declaring [tagSize] bytes of frames, syncsafe-encoded. */
    private fun id3Header(tagSize: Int, flags: Int = 0) = byteArrayOf(
        'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
        3, 0, flags.toByte(),
        ((tagSize shr 21) and 0x7F).toByte(),
        ((tagSize shr 14) and 0x7F).toByte(),
        ((tagSize shr 7) and 0x7F).toByte(),
        (tagSize and 0x7F).toByte(),
    )

    /**
     * An Ogg page carrying [packet] as its first (and only) segment: 27 fixed header
     * bytes, then one length byte per segment, then the packet itself.
     */
    private fun oggPage(packet: ByteArray) =
        magic("OggS") + padding(22) + byteArrayOf(1, packet.size.toByte()) + packet

    /** FLAC frames start with 0xFFF8, which also satisfies the MPEG sync mask. */
    private fun flacAudio() = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69, 0x18)

    @Test
    fun detectsPlainFlac() {
        assertEquals("flac", sniff(magic("fLaC"), padding(64)))
    }

    @Test
    fun detectsPlainOgg() {
        assertEquals("ogg", sniff(oggPage(magic("vorbis")), padding(64)))
    }

    /**
     * Ogg is a container, not a codec. Opus lives in one too, and JAudiotagger reaches its
     * (working) Opus reader only through the `.opus` extension — call the file "ogg" and
     * the Vorbis reader rejects it, making the file unrewritable for no reason.
     */
    @Test
    fun tellsOpusApartFromOggVorbis() {
        assertEquals("opus", sniff(oggPage(magic("OpusHead")), padding(64)))
    }

    /** Anything else in an Ogg stream keeps the plain Ogg handling it had. */
    @Test
    fun treatsAnUnrecognizedOggCodecAsOgg() {
        assertEquals("ogg", sniff(oggPage(magic("theora")), padding(64)))
    }

    @Test
    fun tellsOpusApartEvenBehindAnId3Tag() {
        assertEquals(
            "opus",
            sniff(id3Header(300), padding(300), oggPage(magic("OpusHead")), padding(64)),
        )
    }

    /** A page header too short to hold its own segment table decides nothing. */
    @Test
    fun fallsBackToOggWhenThePageHeaderIsCutOff() {
        assertEquals("ogg", sniff(magic("OggS"), padding(8)))
    }

    @Test
    fun detectsMp4ByFtypAtOffsetFour() {
        assertEquals("m4a", sniff(padding(4), magic("ftyp"), magic("M4A ")))
    }

    @Test
    fun detectsMp3FromAnId3Tag() {
        assertEquals("mp3", sniff(id3Header(300), padding(300), padding(64)))
    }

    @Test
    fun detectsMp3FromABareFrameSync() {
        assertEquals("mp3", sniff(byteArrayOf(0xFF.toByte(), 0xFB.toByte()), padding(64)))
    }

    /**
     * The file this exists for: an EAC rip is a FLAC with an ID3v2 tag glued on the
     * front, so `ID3` at offset 0 is not the container. Calling it mp3 hands it to the
     * MP3 writer, which zeroes the audio out to the first frame sync it finds — and
     * FLAC's own frames provide one.
     */
    @Test
    fun looksPastAnId3TagToFindFlac() {
        assertEquals("flac", sniff(id3Header(300), padding(300), magic("fLaC"), flacAudio()))
    }

    @Test
    fun looksPastAnId3TagToFindOgg() {
        assertEquals(
            "ogg",
            sniff(id3Header(300), padding(300), oggPage(magic("vorbis")), padding(64)),
        )
    }

    /** The flags byte can declare a 10-byte footer, which shifts where the audio starts. */
    @Test
    fun accountsForAnId3FooterWhenLocatingTheAudio() {
        assertEquals(
            "flac",
            sniff(id3Header(300, flags = 0x10), padding(300 + 10), magic("fLaC"), flacAudio()),
        )
    }

    /** A tag running past the end of the file tells us nothing; the name-based guess stands. */
    @Test
    fun fallsBackToMp3WhenTheDeclaredTagOverrunsTheFile() {
        assertEquals("mp3", sniff(id3Header(9_000_000), padding(300)))
    }

    /** Syncsafe integers never set the high bit, so these are not the bytes we assumed. */
    @Test
    fun fallsBackToMp3OnANonSyncsafeSize() {
        val header = id3Header(300).also { it[7] = 0xFF.toByte() }
        assertEquals("mp3", sniff(header, padding(300), magic("fLaC")))
    }

    @Test
    fun returnsNullForUnrecognizedBytes() {
        assertNull(sniff(magic("not audio at all"), padding(32)))
    }

    @Test
    fun returnsNullForAFileTooShortToIdentify() {
        assertNull(sniff(magic("fLa")))
    }

    @Test
    fun returnsNullForAnEmptyFile() {
        assertNull(ContainerSniffer.extensionOf(folder.newFile()))
    }
}
