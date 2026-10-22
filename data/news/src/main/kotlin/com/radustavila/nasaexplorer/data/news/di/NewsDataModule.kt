package com.radustavila.nasaexplorer.data.news.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.news.BuildConfig
import com.radustavila.nasaexplorer.data.news.cache.NewsCacheEvictor
import com.radustavila.nasaexplorer.data.news.local.NewsDao
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.NewsLocalDataSource
import com.radustavila.nasaexplorer.data.news.local.RoomNewsLocalDataSource
import com.radustavila.nasaexplorer.data.news.remote.NewsFeedService
import com.radustavila.nasaexplorer.data.news.remote.NewsFeedUrls
import com.radustavila.nasaexplorer.data.news.remote.NewsRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.RetrofitNewsRemoteDataSource
import com.radustavila.nasaexplorer.data.news.repository.NewsRepositoryImpl
import com.radustavila.nasaexplorer.data.news.search.NewsSearchableSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import java.time.Duration
import javax.inject.Singleton
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
interface NewsDataModule {

    @Binds
    fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository

    @Binds
    fun bindNewsLocalDataSource(impl: RoomNewsLocalDataSource): NewsLocalDataSource

    @Binds
    fun bindNewsRemoteDataSource(impl: RetrofitNewsRemoteDataSource): NewsRemoteDataSource

    @Binds
    @IntoSet
    fun bindNewsCacheEvictor(impl: NewsCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindNewsSearchableSource(impl: NewsSearchableSource): SearchableSource
}

@Module
@InstallIn(SingletonComponent::class)
object NewsInfrastructureModule {

    @Provides
    @Singleton
    fun provideNewsDatabase(@ApplicationContext context: Context): NewsDatabase =
        Room.databaseBuilder(context, NewsDatabase::class.java, NewsDatabase.NAME)
            // A mirror of public feeds without user data: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideNewsDao(database: NewsDatabase): NewsDao = database.newsDao()

    @Provides
    fun provideNewsFeedUrls(): NewsFeedUrls = NewsFeedUrls(FEED_URLS)

    @Provides
    @Singleton
    fun provideNewsFeedService(retrofitFactory: RetrofitFactory): NewsFeedService =
        // Every call passes a full URL; the base only has to be valid.
        retrofitFactory.create(baseUrl = "https://${host(NewsFeed.STORIES)}/").create()

    /**
     * Three feeds on www.nasa.gov, asked once per refresh. The image loader shares the limiter, and
     * the host also serves the pictures of the News list (up to 60 Images of the Day), so the budget
     * is sized for a first scroll through the list rather than for the feeds alone.
     */
    @Provides
    @IntoSet
    fun provideNasaWwwRateBudget(): HostRateBudget =
        HostRateBudget(host = host(NewsFeed.STORIES), maxRequests = 300, window = Duration.ofHours(1))

    /**
     * science.nasa.gov also serves APOD (`:data:nasa`), and the strictest budget for a host wins,
     * so this one is generous enough never to hold back paging through APOD's archive.
     */
    @Provides
    @IntoSet
    fun provideNasaScienceRateBudget(): HostRateBudget =
        HostRateBudget(host = host(NewsFeed.SCIENCE), maxRequests = 600, window = Duration.ofHours(1))

    /** YouTube publishes no limit for its channel feeds; one call per refresh needs very little. */
    @Provides
    @IntoSet
    fun provideYouTubeRateBudget(): HostRateBudget =
        HostRateBudget(host = host(NewsFeed.YOUTUBE), maxRequests = 30, window = Duration.ofHours(1))

    private val FEED_URLS = mapOf(
        NewsFeed.STORIES to BuildConfig.NASA_STORIES_FEED_URL,
        NewsFeed.NEWS_RELEASES to BuildConfig.NASA_NEWS_RELEASES_FEED_URL,
        NewsFeed.SCIENCE to BuildConfig.NASA_SCIENCE_FEED_URL,
        NewsFeed.IMAGE_OF_THE_DAY to BuildConfig.NASA_IMAGE_OF_THE_DAY_FEED_URL,
        NewsFeed.YOUTUBE to BuildConfig.NASA_YOUTUBE_FEED_URL,
    )

    private fun host(feed: NewsFeed): String = URI(FEED_URLS.getValue(feed)).host
}
