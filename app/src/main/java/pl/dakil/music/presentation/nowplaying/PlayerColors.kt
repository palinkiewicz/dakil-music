package pl.dakil.music.presentation.nowplaying

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.music.ui.theme.LocalDarkTheme
import pl.dakil.music.ui.theme.LocalPlayerArtColors
import pl.dakil.music.ui.theme.LocalPureBlack
import pl.dakil.music.ui.theme.animateColorScheme
import pl.dakil.music.ui.theme.toPureBlack

/** Seed colors per cover-art model, so revisiting a song recolors instantly. */
private val seedCache = LruCache<Any, Color>(64)

/** The art's seed sits in a small bitmap; 96px keeps quantizing well under a frame's budget. */
private const val SEED_SAMPLE_PX = 96

/**
 * Themes [content] (the player, album pages) with a scheme built from [artModel]'s dominant color
 * (Material's Content style, which keeps the cover's own hue in the containers). The scheme
 * cross-fades as songs change.
 * Falls back to the app theme when the user turned art colors off or the art has no usable color.
 */
@Composable
fun ArtTheme(artModel: Any?, content: @Composable () -> Unit) {
    val enabled = LocalPlayerArtColors.current
    val dark = LocalDarkTheme.current
    val pureBlack = LocalPureBlack.current
    val appScheme = MaterialTheme.colorScheme
    val seed = rememberArtSeed(if (enabled) artModel else null)

    val target: ColorScheme = remember(seed, dark, pureBlack, appScheme) {
        if (seed == null) {
            appScheme
        } else {
            // Content keeps the cover's hue in primary; secondaries and tertiaries come from
            // TonalSpot so tonal controls stay calm instead of all competing as accents.
            val calm = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = PaletteStyle.TonalSpot)
            dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = PaletteStyle.Content)
                .copy(
                    secondary = calm.secondary,
                    onSecondary = calm.onSecondary,
                    secondaryContainer = calm.secondaryContainer,
                    onSecondaryContainer = calm.onSecondaryContainer,
                    tertiary = calm.tertiary,
                    onTertiary = calm.onTertiary,
                    tertiaryContainer = calm.tertiaryContainer,
                    onTertiaryContainer = calm.onTertiaryContainer,
                )
                .let { if (pureBlack) it.toPureBlack() else it }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = animateColorScheme(target),
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        // Content drawn straight on the themed background (no Surface in between) must still get
        // this scheme's ink, or text falls back to black.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

@Composable
private fun rememberArtSeed(artModel: Any?): Color? {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(artModel?.let { seedCache.get(it) }) }
    LaunchedEffect(artModel) {
        if (artModel == null) {
            seed = null
            return@LaunchedEffect
        }
        seedCache.get(artModel)?.let {
            seed = it
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(artModel)
            .size(SEED_SAMPLE_PX)
            .allowHardware(false)
            .build()
        val bitmap: Bitmap? = (context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable?.toBitmap(SEED_SAMPLE_PX, SEED_SAMPLE_PX, Bitmap.Config.ARGB_8888)
        val found = bitmap?.let { withContext(Dispatchers.Default) { it.asImageBitmap().themeColorOrNull() } }
        if (found != null) seedCache.put(artModel, found)
        seed = found
    }
    return seed
}
