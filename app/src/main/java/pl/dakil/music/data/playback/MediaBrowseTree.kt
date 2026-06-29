package pl.dakil.music.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import pl.dakil.music.domain.model.Song

/**
 * Builds the browsable content hierarchy exposed to media browsers such as Android
 * Auto. The tree is derived entirely from the cached library [Song] list:
 *
 * ```
 * root
 *  ├─ Albums  → <album> → <song>
 *  ├─ Artists → <artist> → <song>
 *  └─ Genres  → <genre>  → <song>
 * ```
 *
 * Song leaves reuse [MediaItemMapper], so their media id is the bare song id and they
 * already carry a playable URI — letting the session resolve playback by id.
 */
object MediaBrowseTree {

    const val ROOT_ID = "root"
    private const val CAT_ALBUMS = "cat_albums"
    private const val CAT_ARTISTS = "cat_artists"
    private const val CAT_GENRES = "cat_genres"
    private const val ALBUM_PREFIX = "album:"
    private const val ARTIST_PREFIX = "artist:"
    private const val GENRE_PREFIX = "genre:"

    /** Nodes whose children depend on the library; browsers are re-notified when it changes. */
    val dynamicParentIds: List<String> = listOf(ROOT_ID, CAT_ALBUMS, CAT_ARTISTS, CAT_GENRES)

    fun rootItem(): MediaItem = browsableItem(
        id = ROOT_ID,
        title = "",
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    )

    /** Children of any node, or an empty list for an unknown / leaf parent. */
    fun children(parentId: String, songs: List<Song>, categoryTitles: CategoryTitles): List<MediaItem> =
        when {
            parentId == ROOT_ID -> listOf(
                browsableItem(CAT_ALBUMS, categoryTitles.albums, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsableItem(CAT_ARTISTS, categoryTitles.artists, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                browsableItem(CAT_GENRES, categoryTitles.genres, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES),
            )

            parentId == CAT_ALBUMS -> albumFolders(songs)
            parentId == CAT_ARTISTS -> artistFolders(songs)
            parentId == CAT_GENRES -> genreFolders(songs)

            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = parentId.removePrefix(ALBUM_PREFIX).toLongOrNull()
                songs.filter { it.albumId == albumId }
                    .sortedBy { it.trackNumber }
                    .map(MediaItemMapper::toMediaItem)
            }

            parentId.startsWith(ARTIST_PREFIX) -> {
                val name = parentId.removePrefix(ARTIST_PREFIX)
                songs.filter { song -> song.artists.any { it.equals(name, ignoreCase = true) } }
                    .sortedBy { it.title.lowercase() }
                    .map(MediaItemMapper::toMediaItem)
            }

            parentId.startsWith(GENRE_PREFIX) -> {
                val name = parentId.removePrefix(GENRE_PREFIX)
                songs.filter { it.genre.trim().equals(name, ignoreCase = true) }
                    .sortedBy { it.title.lowercase() }
                    .map(MediaItemMapper::toMediaItem)
            }

            else -> emptyList()
        }

    /** Resolves a single node by id (for `onGetItem`); null when it can't be found. */
    fun item(mediaId: String, songs: List<Song>, categoryTitles: CategoryTitles): MediaItem? = when {
        mediaId == ROOT_ID -> rootItem()
        mediaId == CAT_ALBUMS ->
            browsableItem(CAT_ALBUMS, categoryTitles.albums, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
        mediaId == CAT_ARTISTS ->
            browsableItem(CAT_ARTISTS, categoryTitles.artists, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        mediaId == CAT_GENRES ->
            browsableItem(CAT_GENRES, categoryTitles.genres, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
        mediaId.startsWith(ALBUM_PREFIX) -> albumFolders(songs).firstOrNull { it.mediaId == mediaId }
        mediaId.startsWith(ARTIST_PREFIX) -> artistFolders(songs).firstOrNull { it.mediaId == mediaId }
        mediaId.startsWith(GENRE_PREFIX) -> genreFolders(songs).firstOrNull { it.mediaId == mediaId }
        else -> songs.firstOrNull { it.id.toString() == mediaId }?.let(MediaItemMapper::toMediaItem)
    }

    private fun albumFolders(songs: List<Song>): List<MediaItem> =
        songs.filter { it.album.isNotBlank() }
            .groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                browsableItem(
                    id = "$ALBUM_PREFIX$albumId",
                    title = first.album,
                    subtitle = first.artistsLabel,
                    artworkUri = first.albumArtUri,
                    mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                )
            }
            .sortedBy { it.mediaMetadata.title?.toString()?.lowercase() }

    private fun artistFolders(songs: List<Song>): List<MediaItem> =
        songs.flatMap { it.artists }
            .distinct()
            .sortedBy { it.lowercase() }
            .map { name ->
                browsableItem("$ARTIST_PREFIX$name", name, mediaType = MediaMetadata.MEDIA_TYPE_ARTIST)
            }

    private fun genreFolders(songs: List<Song>): List<MediaItem> =
        songs.map { it.genre.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.lowercase() }
            .map { name ->
                browsableItem("$GENRE_PREFIX$name", name, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
            }

    private fun browsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: android.net.Uri? = null,
        mediaType: Int,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Localized titles for the top-level category folders. */
    data class CategoryTitles(val albums: String, val artists: String, val genres: String)
}
