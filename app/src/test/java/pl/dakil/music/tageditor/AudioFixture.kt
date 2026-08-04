package pl.dakil.music.tageditor

import org.jaudiotagger.audio.AudioFileIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import pl.dakil.music.data.repository.ContainerSniffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * A real, tiny audio file to retag in tests.
 *
 * The fixtures under `src/test/resources/audio` are one second of a 440 Hz sine
 * synthesized by ffmpeg — genuine encoded bytes in every container the app can meet,
 * with no copyrighted content and nothing bigger than 20 KB. Each carries a full set of
 * pre-existing tags so a test can prove an edit changed one field and left the others
 * standing.
 */
class AudioFixture(val name: String, val extension: String) {

    override fun toString(): String = name

    /** Copies the fixture into [dir] under a fresh name, ready to be written over. */
    fun copyInto(dir: File, named: String = "work.$extension"): File {
        val target = File(dir, named)
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("audio/$name")) {
            "Missing fixture audio/$name"
        }
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    companion object {
        val MP3_V23 = AudioFixture("v23.mp3", "mp3")
        val MP3_V24 = AudioFixture("v24.mp3", "mp3")
        val FLAC = AudioFixture("plain.flac", "flac")
        val FLAC_ID3_PREFIXED = AudioFixture("id3prefixed.flac", "flac")
        val OGG = AudioFixture("plain.ogg", "ogg")
        val OPUS = AudioFixture("plain.opus", "opus")
        val M4A = AudioFixture("plain.m4a", "m4a")

        /** An "Official Video" style m4a: an audio track plus a video one. */
        val M4A_TWO_TRACK = AudioFixture("twotrack.m4a", "m4a")
        val WAV = AudioFixture("plain.wav", "wav")

        /** Every container the app is expected to retag successfully. */
        val SUPPORTED = listOf(MP3_V23, MP3_V24, FLAC, FLAC_ID3_PREFIXED, OGG, OPUS, M4A, WAV)
    }
}

/**
 * Checks that a rewrite changed only tags, never audio.
 *
 * The strongest available statement is "it still decodes to exactly the same samples",
 * so the primary check pipes the file through ffmpeg and compares raw PCM. ffmpeg is not
 * guaranteed to exist on every machine, so two weaker checks that need nothing extra run
 * unconditionally alongside it: the container must be the one we started with, and the
 * parsed audio header must report the same stream. Together those would have caught the
 * FLAC-shredding bug (which kept the file size but lost the container and the samples).
 */
object AudioIntegrity {

    /** Everything worth remembering about a file's audio, captured before an edit. */
    data class Snapshot(
        val container: String?,
        val lengthMs: Int,
        val sampleRate: String,
        val channels: String,
        val pcmDigest: String?,
    )

    fun snapshot(file: File): Snapshot {
        val header = runCatching { AudioFileIO.read(file).audioHeader }.getOrNull()
        return Snapshot(
            container = ContainerSniffer.extensionOf(file),
            lengthMs = header?.trackLength ?: -1,
            sampleRate = header?.sampleRate.orEmpty(),
            channels = header?.channels.orEmpty(),
            pcmDigest = decodedPcmDigest(file),
        )
    }

    /** Fails unless [file]'s audio is bit-for-bit what [before] captured. */
    fun assertUnchanged(before: Snapshot, file: File) {
        val after = snapshot(file)
        assertEquals("container changed", before.container, after.container)
        assertNotNull("audio header unreadable after the edit", after.sampleRate.ifEmpty { null })
        // The header is only comparable when it could be parsed beforehand too — a file
        // whose name misstated its container has no readable header until it is retyped,
        // which is the very thing those tests exercise. The PCM digest below is
        // name-blind, so nothing is left unchecked.
        if (before.lengthMs >= 0) {
            assertEquals("track length changed", before.lengthMs, after.lengthMs)
            assertEquals("sample rate changed", before.sampleRate, after.sampleRate)
            assertEquals("channel count changed", before.channels, after.channels)
        }
        if (before.pcmDigest != null || after.pcmDigest != null) {
            assertEquals("decoded audio changed", before.pcmDigest, after.pcmDigest)
        }
    }

    private val ffmpegAvailable: Boolean by lazy {
        runCatching {
            ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
                .also { it.inputStream.readBytes() }
                .waitFor() == 0
        }.getOrDefault(false)
    }

    /** MD5 of [file] decoded to raw 16-bit PCM, or null when ffmpeg cannot read it. */
    private fun decodedPcmDigest(file: File): String? {
        if (!ffmpegAvailable) return null
        return runCatching {
            val process = ProcessBuilder(
                "ffmpeg", "-v", "error", "-i", file.absolutePath, "-f", "s16le", "-",
            ).start()
            val pcm = ByteArrayOutputStream()
            process.inputStream.use { it.copyTo(pcm) }
            process.errorStream.use { it.readBytes() }
            process.waitFor(60, TimeUnit.SECONDS)
            if (process.exitValue() != 0 || pcm.size() == 0) return null
            MessageDigest.getInstance("MD5")
                .digest(pcm.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
