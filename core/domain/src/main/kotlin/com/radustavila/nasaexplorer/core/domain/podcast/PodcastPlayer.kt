package com.radustavila.nasaexplorer.core.domain.podcast

import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import java.time.Duration
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's one podcast player (F21). It keeps playing in the background, with lock-screen and
 * headset controls, and saves each episode's position as it goes. The Media3 implementation lives
 * in `:core:media`; screens only see this contract.
 *
 * Every call is made from the main thread.
 */
interface PodcastPlayer {

    val playback: StateFlow<PodcastPlayback>

    /**
     * Plays [episode] from where the user left off: the downloaded copy if there is one, else the
     * stream. Offline without a download, nothing plays and [PodcastPlayback.error] says why.
     * Playing the loaded episode again resumes it.
     */
    fun play(episode: PodcastEpisode)

    fun pause()

    /** Resumes the loaded episode. */
    fun resume()

    fun seekTo(position: Duration)

    /** Skips forwards (positive) or back (negative) within the episode. */
    fun seekBy(offset: Duration)

    fun setSpeed(speed: Float)

    /** Saves the position, stops and unloads the episode. */
    fun stop()

    /** Forgets the last error, once it has been shown. */
    fun clearError()

    companion object {
        val SKIP_BACK: Duration = Duration.ofSeconds(15)
        val SKIP_FORWARD: Duration = Duration.ofSeconds(30)
        val SPEEDS: List<Float> = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)
    }
}
