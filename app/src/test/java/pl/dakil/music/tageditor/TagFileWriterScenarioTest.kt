package pl.dakil.music.tageditor

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jcodec.containers.mp4.MP4Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.dakil.music.data.repository.ContainerSniffer
import pl.dakil.music.data.repository.TagFileWriter
import pl.dakil.music.data.repository.UnsupportedFileException
import pl.dakil.music.domain.repository.TagEdit
import java.io.File
import java.io.RandomAccessFile
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The awkward files, as opposed to the clean matrix: the ones that have actually cost a
 * user their audio, or would if the writer stopped being careful.
 *
 * Every case here asserts the same bottom line — either the file comes back correct, or
 * it is refused with [UnsupportedFileException] and left for the caller to skip. What
 * must never happen is a file that is written back damaged.
 */
class TagFileWriterScenarioTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun quietJAudiotagger() {
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        TagOptionSingleton.getInstance().isAndroid = false
    }

    private fun write(file: File, edit: TagEdit = TagEdit(title = "Edited")): File =
        TagFileWriter.write(file, edit, folder.root)

    /**
     * The bug that destroyed a real file: an EAC rip is a FLAC with an ID3v2 tag glued on
     * the front. Trusting that `ID3` meant mp3 handed it to the MP3 writer, which padded
     * its tag out to the first MPEG frame sync — and FLAC frames start with 0xFFF8, which
     * qualifies — zeroing nine megabytes of audio while keeping the file size.
     */
    @Test
    fun anId3PrefixedFlacKeepsItsAudio() {
        val file = AudioFixture.FLAC_ID3_PREFIXED.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)
        assertEquals("flac", before.container)

        val written = write(file, TagEdit(title = "Edited", year = "2016"))

        assertEquals("flac", ContainerSniffer.extensionOf(written))
        assertEquals("Edited", AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * A file whose name lies about its container. JAudiotagger picks its reader — and
     * writer — from the extension alone, so the working copy has to be renamed to what
     * the magic bytes say before anything touches it.
     */
    @Test
    fun anM4aNamedMp3IsWrittenAsAnM4a() {
        val file = AudioFixture.M4A.copyInto(folder.newFolder(), named = "misnamed.mp3")
        val before = AudioIntegrity.snapshot(file)

        val written = write(file)

        assertTrue("should have been retyped to m4a", written.name.endsWith(".m4a"))
        assertEquals("Edited", AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /** The same trick the other way round: a real mp3 carrying a .flac name. */
    @Test
    fun anMp3NamedFlacIsWrittenAsAnMp3() {
        val file = AudioFixture.MP3_V23.copyInto(folder.newFolder(), named = "misnamed.flac")
        val before = AudioIntegrity.snapshot(file)

        val written = write(file)

        assertTrue("should have been retyped to mp3", written.name.endsWith(".mp3"))
        assertEquals("Edited", AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * Opus rides in an Ogg container, so `OggS` alone does not identify it — and calling
     * it "ogg" sends it to the Vorbis reader, which rejects it ("no vorbiscomment
     * header"). JAudiotagger has a perfectly good Opus reader; it is reachable only
     * through the `.opus` extension, so the sniffer has to look at the first packet.
     */
    @Test
    fun anOpusFileIsRecognizedAsOpusNotOgg() {
        val file = AudioFixture.OPUS.copyInto(folder.newFolder(), named = "sounds-like.ogg")
        assertEquals("opus", ContainerSniffer.extensionOf(file))
        val before = AudioIntegrity.snapshot(file)

        val written = write(file)

        assertTrue("should have been retyped to opus", written.name.endsWith(".opus"))
        assertEquals("Edited", AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * A multi-track M4A — the "Official Video" download, audio plus a video or text
     * track. JCodec's MP4 rewrite (which JAudiotagger's MP4 writer uses) collapses a moov
     * to a *single* track by removing every `trak` and re-adding only the last one, so
     * editing one of these would silently discard the audio. The writer pre-empts that by
     * reducing the file to its audio track first; the extra track is dropped on purpose.
     */
    @Test
    fun aMultiTrackM4aKeepsItsAudioAndLosesTheOtherTrack() {
        val file = AudioFixture.M4A_TWO_TRACK.copyInto(folder.newFolder())
        assertEquals("fixture should start with two tracks", 2, trackCount(file))
        val before = AudioIntegrity.snapshot(file)

        val written = write(file, TagEdit(title = "Edited", artist = "Edited Artist"))

        assertEquals("should have been reduced to the audio track", 1, trackCount(written))
        val tag = AudioFileIO.read(written).tag
        assertEquals("Edited", tag.getFirst(FieldKey.TITLE))
        assertEquals("Edited Artist", tag.getFirst(FieldKey.ARTIST))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /** A second edit of an already-reduced file must still work — and still keep the audio. */
    @Test
    fun aReducedM4aCanBeEditedAgain() {
        val file = AudioFixture.M4A_TWO_TRACK.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)

        var written = write(file, TagEdit(title = "First"))
        written = write(written, TagEdit(title = "Second", year = "2012"))

        val tag = AudioFileIO.read(written).tag
        assertEquals("Second", tag.getFirst(FieldKey.TITLE))
        assertEquals("2012", tag.getFirst(FieldKey.YEAR))
        AudioIntegrity.assertUnchanged(before, written)
    }

    private fun trackCount(file: File): Int = RandomAccessFile(file, "r").use { raf ->
        MP4Util.parseFullMovieChannel(raf.channel).moov.tracks.size
    }

    /** Bytes that are not audio at all must be refused, not rewritten into something. */
    @Test
    fun aFileThatIsNotAudioIsRefused() {
        val file = File(folder.newFolder(), "junk.mp3")
        file.writeBytes(ByteArray(4096) { (it % 251).toByte() })

        assertThrowsUnsupported { write(file) }
    }

    /**
     * A truncated file — a half-finished download — has already lost its audio before we
     * arrive, so refusing it is not required. What is required is that we do not make it
     * worse: either the write is refused, or it goes through without touching what audio
     * is left.
     */
    @Test
    fun aTruncatedFileIsNeverMadeWorse() {
        val source = AudioFixture.FLAC.copyInto(folder.newFolder())
        val truncated = File(folder.newFolder(), "half.flac")
        truncated.writeBytes(source.readBytes().copyOf(300))
        val before = AudioIntegrity.snapshot(truncated)

        val written = try {
            write(truncated)
        } catch (refused: UnsupportedFileException) {
            return
        }
        AudioIntegrity.assertUnchanged(before, written)
    }

    /** An empty edit is a no-op the UI can still send; it must not disturb the file. */
    @Test
    fun anEmptyEditLeavesEverythingAsItWas() {
        val file = AudioFixture.MP3_V23.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)
        val title = AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE)

        val written = write(file, TagEdit())

        assertEquals(title, AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * Non-Latin text is the everyday case for this library, and the encoding a container
     * picks for it is exactly the sort of thing that regresses unnoticed.
     */
    @Test
    fun nonLatinTextSurvivesEveryContainer() {
        AudioFixture.SUPPORTED.forEach { fixture ->
            val file = fixture.copyInto(folder.newFolder())
            val written = write(
                file,
                TagEdit(title = "Nic się nie stało", artist = "Мельница", album = "四月は君の嘘"),
            )
            val tag = AudioFileIO.read(written).tag
            assertEquals(fixture.name, "Nic się nie stało", tag.getFirst(FieldKey.TITLE))
            assertEquals(fixture.name, "Мельница", tag.getFirst(FieldKey.ARTIST))
            assertEquals(fixture.name, "四月は君の嘘", tag.getFirst(FieldKey.ALBUM))
        }
    }

    /** Clearing a value is expressed as an empty string, and must actually clear it. */
    @Test
    fun anEmptyValueClearsTheField() {
        AudioFixture.SUPPORTED.forEach { fixture ->
            val file = fixture.copyInto(folder.newFolder())
            val written = write(file, TagEdit(title = ""))
            assertEquals(fixture.name, "", AudioFileIO.read(written).tag.getFirst(FieldKey.TITLE))
        }
    }

    private fun assertThrowsUnsupported(block: () -> Unit) {
        try {
            block()
        } catch (expected: UnsupportedFileException) {
            return
        }
        throw AssertionError("Expected the file to be refused with UnsupportedFileException")
    }
}
