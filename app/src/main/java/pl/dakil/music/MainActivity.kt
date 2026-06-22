package pl.dakil.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.presentation.navigation.MusicApp
import pl.dakil.music.presentation.permissions.AudioPermissionGate
import pl.dakil.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MusicApplication).container

    // Emits whenever an intent asks to open the Now Playing screen (notification tap).
    // replay = 1 so a signal raised before the NavHost subscribes is still delivered.
    private val openNowPlaying = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by container.observeSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            MusicTheme(
                darkTheme = isSystemInDarkTheme() || settings.forceDarkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                AudioPermissionGate(onGranted = ::scanLibrary) {
                    MusicApp(navigateToNowPlaying = openNowPlaying)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlaying.tryEmit(Unit)
        }
    }

    /** Kicks off the initial MediaStore scan after the user grants audio access. */
    private fun scanLibrary() {
        lifecycleScope.launch { container.refreshLibrary() }
    }

    companion object {
        /** Intent extra set by the playback notification to jump to Now Playing. */
        const val EXTRA_OPEN_NOW_PLAYING = "pl.dakil.music.OPEN_NOW_PLAYING"
    }
}
