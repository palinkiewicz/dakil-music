package pl.dakil.music.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A usage meter: an expressive wavy bar that springs to each new [fraction] instead of jumping.
 * The wave stays still (amplitude only, no travel), so a live value doesn't animate continuously.
 */
@Composable
fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "usage",
    )
    LinearWavyProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) },
        wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = 0.dp,
    )
}
