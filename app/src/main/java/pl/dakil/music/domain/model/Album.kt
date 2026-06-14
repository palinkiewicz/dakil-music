package pl.dakil.music.domain.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
)
