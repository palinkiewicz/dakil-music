package pl.dakil.music.data.playback

import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import pl.dakil.music.MusicApplication

/**
 * Hosts the ExoPlayer and its [MediaSession]. As a [MediaSessionService] it owns
 * background playback, the system Now Playing notification and lock-screen/Bluetooth
 * transport controls for free — the app's UI connects as a MediaController client.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var audioManager: AudioManager

    // Latest auto-pause/resume preferences, kept in sync from the settings store.
    private var autoPauseOnZeroVolume = true
    private var autoResumeOnVolumeRestored = true

    /** True while playback is paused specifically because the media volume hit 0. */
    private var pausedByVolume = false

    /** Fires on any system setting change; we re-read the media volume on each. */
    private var volumeObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause instead of ducking/continuing when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        // Keep the auto-pause preferences fresh (works even with no UI connected).
        val settingsRepository = (application as MusicApplication).container.settingsRepository
        settingsRepository.settings
            .onEach {
                autoPauseOnZeroVolume = it.autoPauseOnZeroVolume
                autoResumeOnVolumeRestored = it.autoResumeOnVolumeRestored
            }
            .launchIn(serviceScope)

        registerVolumeObserver()
    }

    /**
     * Observe media-volume changes via a [ContentObserver] on system settings —
     * this keeps working while the app is backgrounded, unlike a UI-scoped listener.
     */
    private fun registerVolumeObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onVolumeChanged()
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        volumeObserver = observer
    }

    private fun onVolumeChanged() {
        val player = mediaSession?.player ?: return
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0) {
            if (autoPauseOnZeroVolume && player.isPlaying) {
                pausedByVolume = true
                player.pause()
            }
        } else if (pausedByVolume) {
            pausedByVolume = false
            if (autoResumeOnVolumeRestored &&
                !player.isPlaying &&
                player.playbackState != Player.STATE_IDLE
            ) {
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * While auto-paused for zero volume, keep the service in the foreground. Media3
     * normally demotes the service when playback pauses; resuming would then require
     * *starting* a foreground service from the background, which Android 12+ forbids
     * (so the volume-triggered [Player.play] would silently fail). Staying foreground
     * means the resume is just a continuation, allowed from the background.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || pausedByVolume)
    }

    /** Stop the service if the user swipes the app away with nothing playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        volumeObserver?.let(contentResolver::unregisterContentObserver)
        volumeObserver = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
