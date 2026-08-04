package pl.dakil.music.tageditor

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import pl.dakil.music.data.repository.TagFileWriter
import pl.dakil.music.domain.repository.ArtworkData
import pl.dakil.music.domain.repository.TagEdit
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * Runs every editable attribute against every container the app supports, on real
 * (synthetic) audio files.
 *
 * The point is not that some happy path works once — it is that a future change to the
 * writer, or a bump of JAudiotagger, cannot quietly break one container or one field.
 * Every case therefore asserts three things: the value round-trips, the *other* tags
 * survive untouched, and the audio is bit-for-bit what it was.
 */
@RunWith(Parameterized::class)
class TagFileWriterMatrixTest(private val fixture: AudioFixture) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<AudioFixture> = AudioFixture.SUPPORTED

        /** Every value the tag editor can write, with how to read it back. */
        private val FIELDS = listOf(
            Field("title", FieldKey.TITLE, "Edited Title") { copy(title = it) },
            Field("artist", FieldKey.ARTIST, "Edited Artist") { copy(artist = it) },
            Field("album", FieldKey.ALBUM, "Edited Album") { copy(album = it) },
            Field("genre", FieldKey.GENRE, "Post-Rock") { copy(genre = it) },
            Field("year", FieldKey.YEAR, "2012") { copy(year = it) },
            Field("track", FieldKey.TRACK, "7") { copy(trackNumber = it) },
            Field("lyrics", FieldKey.LYRICS, "[00:12.34]a line\n[00:15.00]another") {
                copy(syncedLyrics = it)
            },
        )
    }

    private class Field(
        val name: String,
        val key: FieldKey,
        val value: String,
        val apply: TagEdit.(String) -> TagEdit,
    ) {
        override fun toString() = name
    }

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun quietJAudiotagger() {
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
        // The JVM has no Bitmap; the desktop image handler is the correct one here.
        TagOptionSingleton.getInstance().isAndroid = false
    }

    private fun write(file: File, edit: TagEdit): File =
        TagFileWriter.write(file, edit, folder.root)

    private fun read(file: File, key: FieldKey): String =
        AudioFileIO.read(file).tagOrCreateAndSetDefault.getFirst(key).orEmpty()

    /** One edit carrying every field at once — the "save" a user actually performs. */
    @Test
    fun everyFieldRoundTripsWhenWrittenTogether() {
        val file = fixture.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)

        val edit = FIELDS.fold(TagEdit()) { acc, field -> field.apply(acc, field.value) }
        val written = write(file, edit)

        FIELDS.forEach { field ->
            assertEquals("${fixture.name}: ${field.name}", field.value, read(written, field.key))
        }
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * Each field written on its own must leave every other tag exactly as it was. This is
     * what makes editing one song of a multi-selection safe.
     */
    @Test
    fun editingOneFieldLeavesTheOthersIntact() {
        FIELDS.forEach { field ->
            val file = fixture.copyInto(folder.newFolder())
            val before = AudioIntegrity.snapshot(file)
            val originals = FIELDS.associate { it.name to read(file, it.key) }

            val written = write(file, field.apply(TagEdit(), field.value))

            assertEquals(
                "${fixture.name}: ${field.name} did not round-trip",
                field.value,
                read(written, field.key),
            )
            FIELDS.filter { it !== field }.forEach { other ->
                assertEquals(
                    "${fixture.name}: writing ${field.name} disturbed ${other.name}",
                    originals.getValue(other.name),
                    read(written, other.key),
                )
            }
            AudioIntegrity.assertUnchanged(before, written)
        }
    }

    /** Saving twice must converge, not accumulate — the MP4 data-ref bug showed up here. */
    @Test
    fun repeatedSavesConvergeAndKeepTheAudio() {
        val file = fixture.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)
        val edit = FIELDS.fold(TagEdit()) { acc, field -> field.apply(acc, field.value) }

        var written = write(file, edit)
        written = write(written, edit)
        written = write(written, TagEdit(title = "Third Pass"))

        assertEquals("Third Pass", read(written, FieldKey.TITLE))
        FIELDS.filter { it.key != FieldKey.TITLE }.forEach { field ->
            assertEquals("${fixture.name}: ${field.name}", field.value, read(written, field.key))
        }
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * Cover art is the one edit that changes the file's shape rather than a few bytes, so
     * it gets its own case: it must replace the previous picture rather than pile up, and
     * must not cost the audio.
     */
    @Test
    fun artworkIsReplacedRatherThanAccumulated() {
        val file = fixture.copyInto(folder.newFolder())
        val before = AudioIntegrity.snapshot(file)

        var written = write(file, TagEdit(artwork = ArtworkData(png(8, 8), "image/png")))
        val second = png(16, 16)
        written = write(written, TagEdit(artwork = ArtworkData(second, "image/png")))

        val artworks = AudioFileIO.read(written).tagOrCreateAndSetDefault.artworkList
        assertEquals("${fixture.name}: artwork count", 1, artworks.size)
        assertTrue(
            "${fixture.name}: the stored picture is not the one written last",
            artworks.single().binaryData.contentEquals(second),
        )
        AudioIntegrity.assertUnchanged(before, written)
    }

    /**
     * The year has to land in the tag Android's scanner reads, not only the one the
     * standard prefers — see `ScannerVisibleYear`. Checked here on real files of each
     * container, since the mapping differs per container.
     */
    @Test
    fun yearIsWrittenWhereTheAndroidScannerLooks() {
        val file = fixture.copyInto(folder.newFolder())
        val written = write(file, TagEdit(year = "2012"))

        assertEquals("2012", read(written, FieldKey.YEAR))

        // Asserted against the raw bytes on purpose: the scanner reads the file, not
        // JAudiotagger's model of it — and JAudiotagger reads a v2.4 TYER back as a
        // deprecated frame, so its own accessor would hide a value that is really there.
        val bytes = written.readBytes()
        val marker = when (fixture.extension) {
            // The ID3 frame the platform's tag table maps to the year key.
            "mp3", "wav" -> "TYER".toByteArray(Charsets.US_ASCII)
            // The Vorbis comment it maps to the year key (DATE goes to the *date* key).
            "flac", "ogg" -> "YEAR=2012".toByteArray(Charsets.US_ASCII)
            // MP4's ©day already lands on the year key; nothing to mirror.
            else -> null
        } ?: return

        val at = bytes.indexOfSequence(marker)
        assertTrue("${fixture.name}: no ${String(marker)} for the scanner to find", at >= 0)
        assertTrue(
            "${fixture.name}: ${String(marker)} is present but does not carry 2012",
            String(bytes, at, minOf(24, bytes.size - at), Charsets.ISO_8859_1).contains("2012"),
        )
    }

    private fun ByteArray.indexOfSequence(needle: ByteArray): Int =
        (0..size - needle.size).firstOrNull { start ->
            needle.indices.all { this[start + it] == needle[it] }
        } ?: -1

    /** A real PNG of the given size, so the image handler has something valid to measure. */
    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, (x * 31 + y) or 0x404040)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
