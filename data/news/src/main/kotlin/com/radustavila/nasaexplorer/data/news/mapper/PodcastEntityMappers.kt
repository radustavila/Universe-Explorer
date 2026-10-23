package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.PodcastDownload
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.data.news.local.PodcastEpisodeEntity
import com.radustavila.nasaexplorer.data.news.local.PodcastEpisodeWithState
import java.time.Duration
import java.time.Instant

internal fun PodcastEpisode.toEntity(cachedAt: Instant) = PodcastEpisodeEntity(
    id = id,
    show = show.key,
    title = title,
    publishedAtMillis = publishedAt.toEpochMilli(),
    audioUrl = audioUrl,
    durationSeconds = duration?.seconds,
    summary = summary,
    imageUrl = imageUrl,
    pageUrl = pageUrl,
    cachedAtMillis = cachedAt.toEpochMilli(),
)

/** The episode with its progress and download; rows of a show the app no longer follows are skipped. */
internal fun PodcastEpisodeWithState.toPodcastEpisodeOrNull(): PodcastEpisode? {
    val show = PodcastShow.fromKey(episode.show) ?: return null
    return PodcastEpisode(
        id = episode.id,
        show = show,
        title = episode.title,
        publishedAt = Instant.ofEpochMilli(episode.publishedAtMillis),
        audioUrl = episode.audioUrl,
        duration = episode.durationSeconds?.let(Duration::ofSeconds),
        summary = episode.summary,
        imageUrl = episode.imageUrl,
        pageUrl = episode.pageUrl,
        progress = progress?.let { PodcastProgress(Duration.ofMillis(it.positionMillis), it.isFinished) },
        download = download?.let { columns ->
            PodcastDownloadState.entries.firstOrNull { it.name == columns.state }
                ?.let { state -> PodcastDownload(state, columns.bytes) }
        },
    )
}
