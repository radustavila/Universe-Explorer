package com.radustavila.nasaexplorer.core.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps a podcast playing with the app in the background or the screen locked. Media3 shows the
 * media notification (lock screen, quick settings, Bluetooth and wired headsets) while the
 * session's player plays, and runs the service in the foreground meanwhile.
 *
 * The player itself belongs to [Media3PodcastPlayer], which outlives this service: the session
 * is released here, never the player.
 */
@AndroidEntryPoint
class PodcastPlaybackService : MediaSessionService() {

    @Inject
    lateinit var podcastPlayer: Media3PodcastPlayer

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val builder = MediaSession.Builder(this, podcastPlayer.sessionPlayer())
            // Podcasts skip by time rather than to the previous or next track.
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setDisplayName(getString(R.string.podcast_player_skip_back))
                        .setSlots(CommandButton.SLOT_BACK)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setDisplayName(getString(R.string.podcast_player_skip_forward))
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .build(),
                ),
            )
        sessionActivity()?.let(builder::setSessionActivity)
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops a paused podcast; a playing one carries on. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    /** Tapping the notification opens the app where the user left it. */
    private fun sessionActivity(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
