package com.radustavila.nasaexplorer.data.news.di

import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.news.BuildConfig
import com.radustavila.nasaexplorer.data.news.download.PodcastDownloadStorage
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.PodcastDao
import com.radustavila.nasaexplorer.data.news.local.PodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.local.RoomPodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.remote.PodcastFeedUrls
import com.radustavila.nasaexplorer.data.news.remote.PodcastRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.RetrofitPodcastRemoteDataSource
import com.radustavila.nasaexplorer.data.news.repository.PodcastRepositoryImpl
import com.radustavila.nasaexplorer.data.news.search.PodcastSearchableSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration

/**
 * NASA's podcasts (F21). Kept apart from [NewsDataModule] so tests that swap the news for a fake
 * keep these bindings; the feeds share the news module's database and feed service.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PodcastDataModule {

    @Binds
    fun bindPodcastRepository(impl: PodcastRepositoryImpl): PodcastRepository

    @Binds
    fun bindPodcastLocalDataSource(impl: RoomPodcastLocalDataSource): PodcastLocalDataSource

    @Binds
    fun bindPodcastRemoteDataSource(impl: RetrofitPodcastRemoteDataSource): PodcastRemoteDataSource

    @Binds
    @IntoSet
    fun bindPodcastSearchableSource(impl: PodcastSearchableSource): SearchableSource

    @Binds
    @IntoSet
    fun bindPodcastDownloadUsage(impl: PodcastDownloadStorage): StorageUsageSource

    @Binds
    @IntoSet
    fun bindPodcastDownloadCleaner(impl: PodcastDownloadStorage): CacheCleaner
}

@Module
@InstallIn(SingletonComponent::class)
object PodcastInfrastructureModule {

    @Provides
    fun providePodcastDao(database: NewsDatabase): PodcastDao = database.podcastDao()

    @Provides
    fun providePodcastFeedUrls(): PodcastFeedUrls = PodcastFeedUrls(
        mapOf(
            PodcastShow.HOUSTON_WE_HAVE_A_PODCAST to BuildConfig.PODCAST_HOUSTON_FEED_URL,
            PodcastShow.CURIOUS_UNIVERSE to BuildConfig.PODCAST_CURIOUS_UNIVERSE_FEED_URL,
            PodcastShow.SMALL_STEPS_GIANT_LEAPS to BuildConfig.PODCAST_SMALL_STEPS_FEED_URL,
        ),
    )

    /**
     * nasa.gov answers each feed with a redirect to Megaphone, which serves the feed itself.
     * Three feeds per refresh at most every 3 hours need very little; the audio comes from
     * other Megaphone hosts and is not limited here.
     */
    @Provides
    @IntoSet
    fun provideMegaphoneFeedsRateBudget(): HostRateBudget =
        HostRateBudget(host = "feeds.megaphone.fm", maxRequests = 30, window = Duration.ofHours(1))
}
