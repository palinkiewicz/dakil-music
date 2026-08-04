package pl.dakil.music

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.music.data.repository.ScannerVisibleYear

class ScannerVisibleYearTest {

    /**
     * The mp3 case: JAudiotagger writes v2.4's `TDRC`, which the platform ID3 parser
     * has no entry for, so the scanner sees no year. `TYER` is the frame it does read.
     */
    @Test
    fun addsTyerAlongsideTdrcOnAnId3v24Tag() {
        val tag = ID3v24Tag()
        tag.setField(FieldKey.YEAR, "2012")
        ScannerVisibleYear.write(tag, "2012")

        assertEquals("2012", tag.getFirst("TDRC"))
        assertEquals("2012", tag.getFirst("TYER"))
    }

    /** v2.3 already writes `TYER` for [FieldKey.YEAR]; nothing to add, nothing to break. */
    @Test
    fun leavesAnId3v23TagAlone() {
        val tag = ID3v23Tag()
        tag.setField(FieldKey.YEAR, "2012")
        ScannerVisibleYear.write(tag, "2012")

        assertEquals("2012", tag.getFirst("TYER"))
    }

    /**
     * The flac case: `DATE` maps to the extractor's *date* key, not its year key, so a
     * literal `YEAR` comment is what reaches `MediaStore.Audio.Media.YEAR`.
     */
    @Test
    fun addsYearAlongsideDateOnAFlacTag() {
        val tag = FlacTag(VorbisCommentTag.createNewTag(), ArrayList())
        tag.setField(FieldKey.YEAR, "2016")
        ScannerVisibleYear.write(tag, "2016")

        assertEquals("2016", tag.vorbisCommentTag.getFirst("DATE"))
        assertEquals("2016", tag.vorbisCommentTag.getFirst("YEAR"))
    }

    /** The same comment block, reached directly — an Ogg file rather than a FLAC. */
    @Test
    fun addsYearAlongsideDateOnABareVorbisCommentTag() {
        val tag = VorbisCommentTag.createNewTag()
        tag.setField(FieldKey.YEAR, "2016")
        ScannerVisibleYear.write(tag, "2016")

        assertEquals("2016", tag.getFirst("DATE"))
        assertEquals("2016", tag.getFirst("YEAR"))
    }

    /** Re-editing replaces the mirrored value rather than accumulating spellings. */
    @Test
    fun overwritesAPreviouslyMirroredYear() {
        val id3 = ID3v24Tag()
        ScannerVisibleYear.write(id3, "1999")
        ScannerVisibleYear.write(id3, "2012")
        assertEquals("2012", id3.getFirst("TYER"))

        val vorbis = VorbisCommentTag.createNewTag()
        ScannerVisibleYear.write(vorbis, "1999")
        ScannerVisibleYear.write(vorbis, "2016")
        assertEquals("2016", vorbis.getFirst("YEAR"))
        assertEquals(1, vorbis.getFields("YEAR").size)
    }
}
