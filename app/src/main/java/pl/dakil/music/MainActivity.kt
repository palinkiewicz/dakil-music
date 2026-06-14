package pl.dakil.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.presentation.navigation.MusicApp
import pl.dakil.music.presentation.permissions.AudioPermissionGate
import pl.dakil.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MusicApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.observeSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            MusicTheme(
                darkTheme = isSystemInDarkTheme() || settings.forceDarkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                AudioPermissionGate(onGranted = ::scanLibrary) {
                    MusicApp()
                }
            }
        }
    }

    /** Kicks off the initial MediaStore scan after the user grants audio access. */
    private fun scanLibrary() {
        lifecycleScope.launch { container.refreshLibrary() }
    }
}
