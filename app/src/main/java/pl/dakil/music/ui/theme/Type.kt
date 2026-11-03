package pl.dakil.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import pl.dakil.music.R

// Google Sans Flex (OFL-1.1, bundled subset; see tools/subset-font.sh). Display and headline styles
// use the fully rounded ROND axis for the expressive, friendly "big type"; everything else stays crisp.
private fun googleSansFlex(weight: Int, roundness: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", roundness),
    ),
)

val RoundedDisplayFamily: FontFamily = FontFamily((300..900 step 100).map { googleSansFlex(it, roundness = 100f) })
val TextFamily: FontFamily = FontFamily((300..900 step 100).map { googleSansFlex(it, roundness = 0f) })

private fun TextStyle.rounded() = copy(fontFamily = RoundedDisplayFamily)
private fun TextStyle.text() = copy(fontFamily = TextFamily)

/** Tabular (fixed-width) figures, so live values don't shift sideways as their digits change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val base = Typography()

internal val AppTypography = Typography(
    displayLarge = base.displayLarge.rounded(),
    displayMedium = base.displayMedium.rounded(),
    displaySmall = base.displaySmall.rounded(),
    headlineLarge = base.headlineLarge.rounded(),
    headlineMedium = base.headlineMedium.rounded(),
    headlineSmall = base.headlineSmall.rounded(),
    titleLarge = base.titleLarge.text(),
    titleMedium = base.titleMedium.text(),
    titleSmall = base.titleSmall.text(),
    bodyLarge = base.bodyLarge.text(),
    bodyMedium = base.bodyMedium.text(),
    bodySmall = base.bodySmall.text(),
    labelLarge = base.labelLarge.text(),
    labelMedium = base.labelMedium.text(),
    labelSmall = base.labelSmall.text(),
    displayLargeEmphasized = base.displayLargeEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    displayMediumEmphasized = base.displayMediumEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    displaySmallEmphasized = base.displaySmallEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineLargeEmphasized = base.headlineLargeEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineMediumEmphasized = base.headlineMediumEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineSmallEmphasized = base.headlineSmallEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    titleLargeEmphasized = base.titleLargeEmphasized.text(),
    titleMediumEmphasized = base.titleMediumEmphasized.text(),
    titleSmallEmphasized = base.titleSmallEmphasized.text(),
    bodyLargeEmphasized = base.bodyLargeEmphasized.text(),
    bodyMediumEmphasized = base.bodyMediumEmphasized.text(),
    bodySmallEmphasized = base.bodySmallEmphasized.text(),
    labelLargeEmphasized = base.labelLargeEmphasized.text(),
    labelMediumEmphasized = base.labelMediumEmphasized.text(),
    labelSmallEmphasized = base.labelSmallEmphasized.text(),
)
