package pl.dakil.music.data.playback

import androidx.annotation.StringRes
import pl.dakil.music.R

/**
 * App-defined equalizer layout applied through [android.media.audiofx.DynamicsProcessing].
 * Unlike the legacy platform [android.media.audiofx.Equalizer], the band layout and the
 * presets are ours, so the UI and the persisted settings mean the same thing on every
 * device instead of depending on whatever the OEM effect bundle exposes.
 */
object EqualizerSpec {

    /** Center frequency of each band in Hz, for display. */
    val CENTER_FREQS_HZ = listOf(60, 230, 910, 3_600, 14_000)

    /** Upper cutoff of each band in Hz — what [android.media.audiofx.DynamicsProcessing.EqBand] consumes. */
    val CUTOFF_FREQS_HZ = listOf(120f, 460f, 1_800f, 7_000f, 20_000f)

    val BAND_COUNT = CENTER_FREQS_HZ.size

    const val MIN_LEVEL_MB = -1_500
    const val MAX_LEVEL_MB = 1_500

    class Preset(@param:StringRes val nameRes: Int, val levelsMb: List<Int>)

    /** The classic Android preset curves, indexed by the persisted preset id. */
    val PRESETS = listOf(
        Preset(R.string.eq_preset_normal, listOf(300, 0, 0, 0, 300)),
        Preset(R.string.eq_preset_classical, listOf(500, 300, -200, 400, 400)),
        Preset(R.string.eq_preset_dance, listOf(600, 0, 200, 400, 100)),
        Preset(R.string.eq_preset_folk, listOf(300, 0, 0, 200, -100)),
        Preset(R.string.eq_preset_heavy_metal, listOf(400, 100, 900, 300, 0)),
        Preset(R.string.eq_preset_hip_hop, listOf(500, 300, 0, 100, 300)),
        Preset(R.string.eq_preset_jazz, listOf(400, 200, -200, 200, 500)),
        Preset(R.string.eq_preset_pop, listOf(-100, 200, 500, 100, -200)),
        Preset(R.string.eq_preset_rock, listOf(500, 300, -100, 300, 500)),
    )
}
