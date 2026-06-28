package pl.dakil.music.widget

import android.graphics.Bitmap

/**
 * Process-memory cache of the current track's cover-art bitmap, keyed by song id.
 *
 * Decoding art is suspending IO that Glance can't do inside a composition, and the
 * old approach only decoded lazily during a Glance pass — so a widget placed mid-song
 * showed no art until the next track. The updater now decodes art the moment the song
 * changes and stores it here, so [MusicWidget] can read it synchronously and show it
 * immediately.
 */
object WidgetArtHolder {

    @Volatile
    var songId: Long? = null
        private set

    @Volatile
    var art: Bitmap? = null
        private set

    fun set(songId: Long?, art: Bitmap?) {
        this.songId = songId
        this.art = art
    }

    /** The cached bitmap, but only when it actually belongs to [songId]. */
    fun artFor(songId: Long?): Bitmap? = if (songId != null && songId == this.songId) art else null
}
