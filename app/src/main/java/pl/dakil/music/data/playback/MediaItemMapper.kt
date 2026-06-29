package pl.dakil.music.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import pl.dakil.music.domain.model.Song

/** Maps domain [Song]s to Media3 [MediaItem]s with metadata for the Now Playing notification. */
object MediaItemMapper {

    /**
     * Best-effort reverse mapping for items the app didn't enqueue itself (e.g. a track
     * started from Android Auto). Reconstructs a partial [Song] from the [MediaItem]'s id,
     * uri and metadata so the in-app Now Playing screen can still show title/artist/art.
     * Returns null when the item lacks a numeric media id or a playable uri.
     */
    fun toSong(item: MediaItem): Song? {
        val id = item.mediaId.toLongOrNull() ?: return null
        val uri = item.localConfiguration?.uri ?: return null
        val md = item.mediaMetadata
        val artist = md.artist?.toString().orEmpty()
        return Song(
            id = id,
            uri = uri,
            title = md.title?.toString().orEmpty(),
            artists = if (artist.isBlank()) emptyList() else listOf(artist),
            rawArtist = artist,
            album = md.albumTitle?.toString().orEmpty(),
            albumId = 0L,
            albumArtUri = md.artworkUri,
            durationMs = 0L,
            trackNumber = 0,
            year = 0,
            genre = "",
            mimeType = "audio/*",
            dateAddedSeconds = 0L,
        )
    }

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
