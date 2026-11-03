package pl.dakil.music.presentation.permissions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import pl.dakil.music.ui.designsystem.MorphShape
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pl.dakil.music.R

/** The runtime permission required to read audio, by API level. */
val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Gates [content] behind the audio read permission. Shows a rationale + request
 * button until granted, then invokes [onGranted] once (to kick off the initial scan).
 */
@Composable
fun AudioPermissionGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    fun isGranted() = ContextCompat.checkSelfPermission(context, audioPermission) ==
        PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var requestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isOk ->
        granted = isOk
        requestedOnce = true
    }

    // On API 33+ also request notification permission so the Now Playing notification shows.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort, non-blocking */ }

    if (granted) {
        // Fire the initial scan and (on 33+) request notifications, once per grant.
        LaunchedEffect(Unit) {
            onGranted()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        content()
        return
    }

    PermissionRequest(
        deniedPermanently = requestedOnce && !granted,
        onRequest = { launcher.launch(audioPermission) },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

@Composable
private fun PermissionRequest(
    deniedPermanently: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MorphingBadge()
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.m))
            Text(
                text = stringResource(
                    if (deniedPermanently) {
                        R.string.permission_denied_permanently
                    } else {
                        R.string.permission_rationale
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxl))
            Button(
                onClick = if (deniedPermanently) onOpenSettings else onRequest,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    stringResource(if (deniedPermanently) R.string.permission_open_settings else R.string.permission_grant),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Polygons the welcome badge morphs through, in a loop. */
private val BadgeShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Flower,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Sunny,
)

/** A music symbol in a badge that keeps morphing from one expressive shape into the next. */
@Composable
private fun MorphingBadge() {
    val morphs = remember { BadgeShapes.indices.map { Morph(BadgeShapes[it], BadgeShapes[(it + 1) % BadgeShapes.size]) } }
    val transition = rememberInfiniteTransition(label = "badge")
    // One unit per shape: the integer part picks the morph, the fraction is its progress.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = BadgeShapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(BadgeShapes.size * 2_200, easing = LinearEasing)),
        label = "badgePhase",
    )
    val index = phase.toInt().coerceIn(0, morphs.lastIndex)
    // Ease each morph so the badge rests on a shape before moving on.
    val local = FastOutSlowInEasing.transform((phase - index).coerceIn(0f, 1f))
    Box(
        Modifier
            .size(160.dp)
            .graphicsLayer { rotationZ = phase * 45f }
            .background(MaterialTheme.colorScheme.primaryContainer, MorphShape(morphs[index], local)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            AppSymbols.LibraryMusic,
            contentDescription = null,
            filled = true,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer { rotationZ = -phase * 45f },
        )
    }
}
