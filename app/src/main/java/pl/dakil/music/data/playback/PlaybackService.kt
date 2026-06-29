package pl.dakil.music.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import pl.dakil.music.MainActivity
import pl.dakil.music.MusicApplication
import pl.dakil.music.R

/**
 * Hosts the ExoPlayer and its [MediaSession]. As a [MediaSessionService] it owns
 * background playback, the system Now Playing notification and lock-screen/Bluetooth
 * transport controls for free — the app's UI connects as a MediaController client.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var audioManager: AudioManager

    /** Owns the platform audio effects bound to the player's session. */
    private var audioEffects: AudioEffectsController? = null

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

        // Pin a stable audio session id up front so the effects can attach immediately,
        // rather than waiting for the id to materialize on first playback.
        val audioSessionId = audioManager.generateAudioSessionId()
        if (audioSessionId != AudioManager.ERROR) {
            player.setAudioSessionId(audioSessionId)
            audioEffects = AudioEffectsController(audioSessionId)
        }

        val closeButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.notification_action_close))
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setCustomLayout(ImmutableList.of(closeButton))
            .setCallback(SessionCallback())
            // Tapping the notification body opens the app on the Now Playing screen.
            .setSessionActivity(buildNowPlayingActivityIntent())
            .build()

        // Keep the auto-pause preferences fresh (works even with no UI connected).
        val container = (application as MusicApplication).container
        val settingsRepository = container.settingsRepository
        settingsRepository.settings
            .onEach {
                autoPauseOnZeroVolume = it.autoPauseOnZeroVolume
                autoResumeOnVolumeRestored = it.autoResumeOnVolumeRestored
            }
            .launchIn(serviceScope)

        // Apply audio effects from the shared store; the UI writes, the service applies.
        container.audioEffectsRepository.settings
            .onEach { audioEffects?.apply(it) }
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

    /** PendingIntent that brings the app to the foreground on the Now Playing screen. */
    private fun buildNowPlayingActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Handles the custom "close" notification action: it isn't one of the standard
     * transport commands, so the session must advertise it on connect and act on it
     * when invoked.
     */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    connectionResult.availableSessionCommands.buildUpon()
                        .add(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
                        .build()
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE) {
                stopEverything()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * Stops the current song, tears the player's queue down and shuts the service
     * down so the foreground notification is dismissed.
     */
    private fun stopEverything() {
        mediaSession?.player?.run {
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

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
        audioEffects?.release()
        audioEffects = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        /** Custom session command id for the notification's close action. */
        const val ACTION_CLOSE = "pl.dakil.music.action.CLOSE"
    }
}
