package pl.dakil.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier

/** Simple full-row click target. */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

/** Square (1:1) aspect ratio, used for album-art tiles. */
fun Modifier.aspectRatioSquare(): Modifier = aspectRatio(1f)
