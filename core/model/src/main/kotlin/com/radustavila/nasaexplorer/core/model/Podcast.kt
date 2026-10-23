package com.radustavila.nasaexplorer.core.model

import java.time.Duration
import java.time.Instant

/**
 * One of NASA's podcasts the app follows (F21). [key] is persisted with each cached episode: never
 * rename or reuse one. Gravity Assist is left out: it ended in 2022.
 */
enum class PodcastShow(val key: String, val syncSource: SyncSource) {
    /** Houston We Have a Podcast, from Johnson Space Center. */
    HOUSTON_WE_HAVE_A_PODCAST("houston_we_have_a_podcast", SyncSource.NASA_PODCAST_HOUSTON),

    /** NASA's Curious Universe. */
    CURIOUS_UNIVERSE("curious_universe", SyncSource.NASA_PODCAST_CURIOUS_UNIVERSE),

    /** Small Steps, Giant Leaps, from the Academy of Program/Project and Engineering Leadership. */
    SMALL_STEPS_GIANT_LEAPS("small_steps_giant_leaps", SyncSource.NASA_PODCAST_SMALL_STEPS),
    ;

    companion object {
        fun fromKey(key: String): PodcastShow? = entries.firstOrNull { it.key == key }
    }
}

/**
 * An episode of a NASA podcast, as its feed lists it, with what the user did with it on this
 * device. Episodes are streamed from NASA's own feed URLs; [download] is the optional offline copy.
 */
data class PodcastEpisode(
    /** The feed's `guid`, else the audio URL; stable across refreshes. */
    val id: String,
    val show: PodcastShow,
    val title: String,
    val publishedAt: Instant,
    /** The official enclosure URL, streamed as it is (redirects included). */
    val audioUrl: String,
    /** The feed's `itunes:duration`; feeds give no file sizes. */
    val duration: Duration? = null,
    /** The episode's description as plain text. */
    val summary: String? = null,
    /** The episode's artwork, else the show's. */
    val imageUrl: String? = null,
    /** The episode's page on nasa.gov. */
    val pageUrl: String? = null,
    val progress: PodcastProgress? = null,
    val download: PodcastDownload? = null,
) {
    val isDownloaded: Boolean get() = download?.state == PodcastDownloadState.DOWNLOADED
}

/** How far the user listened. */
data class PodcastProgress(
    val position: Duration,
    /** Listened to the end; [position] is zero again then. */
    val isFinished: Boolean = false,
)

enum class PodcastDownloadState { QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

data class PodcastDownload(
    val state: PodcastDownloadState,
    /** The size on disk once [PodcastDownloadState.DOWNLOADED]. */
    val bytes: Long = 0,
)

/** What the podcast player is doing, for the mini player and the full player sheet. */
data class PodcastPlayback(
    /** The episode loaded in the player; `null` when nothing is. */
    val episode: PodcastEpisode? = null,
    /** Playing, or about to as soon as it has buffered. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Duration = Duration.ZERO,
    /** The file's own length once known, else the feed's. */
    val duration: Duration? = null,
    val speed: Float = 1f,
    val error: PodcastPlaybackError? = null,
)

enum class PodcastPlaybackError {
    /** Offline, and the episode was not downloaded. */
    OFFLINE_NOT_DOWNLOADED,

    /** The stream or file could not be played (network lost, server error, unreadable file). */
    CANNOT_PLAY,
}
