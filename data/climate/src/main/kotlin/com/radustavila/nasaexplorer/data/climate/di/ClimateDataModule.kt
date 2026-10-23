package com.radustavila.nasaexplorer.data.climate.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.repository.ClimateRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.climate.BuildConfig
import com.radustavila.nasaexplorer.data.climate.cache.ClimateCacheCleaner
import com.radustavila.nasaexplorer.data.climate.local.ClimateDao
import com.radustavila.nasaexplorer.data.climate.local.ClimateDatabase
import com.radustavila.nasaexplorer.data.climate.local.ClimateLocalDataSource
import com.radustavila.nasaexplorer.data.climate.local.RoomClimateLocalDataSource
import com.radustavila.nasaexplorer.data.climate.quiz.ClimateFunFactSource
import com.radustavila.nasaexplorer.data.climate.quiz.ClimateQuizSource
import com.radustavila.nasaexplorer.data.climate.remote.ClimateFileService
import com.radustavila.nasaexplorer.data.climate.remote.ClimateRemoteDataSource
import com.radustavila.nasaexplorer.data.climate.remote.ClimateSourceUrls
import com.radustavila.nasaexplorer.data.climate.remote.RetrofitClimateRemoteDataSource
import com.radustavila.nasaexplorer.data.climate.repository.ClimateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
internal interface ClimateDataModule {

    @Binds
    fun bindClimateRepository(impl: ClimateRepositoryImpl): ClimateRepository

    @Binds
    fun bindClimateLocalDataSource(impl: RoomClimateLocalDataSource): ClimateLocalDataSource

    @Binds
    fun bindClimateRemoteDataSource(impl: RetrofitClimateRemoteDataSource): ClimateRemoteDataSource

    @Binds
    @IntoSet
    fun bindClimateCacheCleaner(impl: ClimateCacheCleaner): CacheCleaner

    @Binds
    @IntoSet
    fun bindClimateQuizSource(impl: ClimateQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindClimateFunFactSource(impl: ClimateFunFactSource): FunFactSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object ClimateInfrastructureModule {

    @Provides
    @Singleton
    fun provideClimateDatabase(@ApplicationContext context: Context): ClimateDatabase =
        Room.databaseBuilder(context, ClimateDatabase::class.java, ClimateDatabase.NAME)
            // A disposable copy of four public files: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideClimateDao(database: ClimateDatabase): ClimateDao = database.climateDao()

    @Provides
    fun provideClimateSourceUrls(): ClimateSourceUrls = ClimateSourceUrls(
        mapOf(
            ClimateIndicator.CO2 to BuildConfig.CO2_URL.toHttpUrl(),
            ClimateIndicator.GLOBAL_TEMPERATURE to BuildConfig.GISTEMP_URL.toHttpUrl(),
            ClimateIndicator.SEA_LEVEL to BuildConfig.SEA_LEVEL_URL.toHttpUrl(),
            ClimateIndicator.ARCTIC_SEA_ICE to BuildConfig.SEA_ICE_URL.toHttpUrl(),
        ),
    )

    /** The URLs are absolute (`@Url`), so the base URL only satisfies Retrofit. */
    @Provides
    @Singleton
    fun provideClimateFileService(retrofitFactory: RetrofitFactory): ClimateFileService =
        retrofitFactory.create(
            BuildConfig.CO2_URL.toHttpUrl().newBuilder().encodedPath("/").build().toString(),
        ).create()

    /** One small file per host a week; the budget only stops runaway loops. */
    @Provides
    @ElementsIntoSet
    fun provideClimateRateBudgets(urls: ClimateSourceUrls): Set<HostRateBudget> =
        urls.urls.values.map { it.host }.toSet().map { host ->
            HostRateBudget(host = host, maxRequests = REQUESTS_PER_HOUR, window = Duration.ofHours(1))
        }.toSet()

    private const val REQUESTS_PER_HOUR = 6
}
