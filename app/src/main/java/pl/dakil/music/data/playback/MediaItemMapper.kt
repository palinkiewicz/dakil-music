package pl.dakil.music.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import pl.dakil.music.domain.model.Song

/** Maps domain [Song]s to Media3 [MediaItem]s with metadata for the Now Playing notification. */
object MediaItemMapper {

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artistsLabel.ifBlank { song.rawArtist })
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMimeType(song.mimeType)
            .setMediaMetadata(metadata)
            .build()
    }
}
