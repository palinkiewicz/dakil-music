package pl.dakil.music.data.repository

import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTYER
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

/**
 * Adds a second, redundant spelling of the year that Android's media scanner can
 * actually see.
 *
 * `MediaStore.Audio.Media.YEAR` is filled by `ModernMediaScanner` from
 * `MediaMetadataRetriever.METADATA_KEY_YEAR`, which the platform extractor only
 * populates from a fixed tag→key table. That table has two gaps:
 *
 * - **ID3**: it maps `TYER`/`TYE` only. ID3v2.4 replaced those with `TDRC`, which is
 *   what JAudiotagger writes for [org.jaudiotagger.tag.FieldKey.YEAR] on a v2.4 tag —
 *   so a correctly tagged v2.4 mp3 scans with a NULL year.
 * - **Vorbis comments** (FLAC/Ogg): `DATE` maps to the *date* key, not the year one,
 *   and `DATE` is again what JAudiotagger writes. Only a literal `YEAR` comment lands
 *   on `METADATA_KEY_YEAR`.
 *
 * MP4/M4A needs nothing: its `©day` atom already maps to the year key, which is why
 * m4a files are the only ones whose year survives a scan untouched.
 *
 * Both extra spellings are legal and widely understood — `TYER` is simply the older
 * ID3 frame, `YEAR` a conventional Vorbis comment — so other players read the file no
 * differently. Writing them costs nothing and is what makes the year survive a full
 * rescan, rather than living only in the index until the next one.
 */
internal object ScannerVisibleYear {

    /** Mirrors [year] into the tag field the platform extractor reads, where needed. */
    fun write(tag: Tag, year: String) {
        when (tag) {
            is FlacTag -> writeVorbis(tag.vorbisCommentTag, year)
            is VorbisCommentTag -> writeVorbis(tag, year)
            // v2.3 and v2.2 already write TYER/TYE for FieldKey.YEAR.
            is ID3v24Tag -> writeTyer(tag, year)
        }
    }

    private fun writeVorbis(tag: VorbisCommentTag, year: String) {
        tag.setField(tag.createField("YEAR", year))
    }

    private fun writeTyer(tag: ID3v24Tag, year: String) {
        val frame = ID3v24Frame("TYER")
        val body = frame.body as? FrameBodyTYER ?: return
        body.text = year
        tag.setFrame(frame)
    }
}
