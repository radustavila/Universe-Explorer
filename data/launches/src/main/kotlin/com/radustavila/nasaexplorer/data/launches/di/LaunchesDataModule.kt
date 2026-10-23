package com.radustavila.nasaexplorer.data.launches.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderRepository
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderScheduler
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.launches.BuildConfig
import com.radustavila.nasaexplorer.data.launches.local.LaunchDao
import com.radustavila.nasaexplorer.data.launches.local.LaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.launches.local.PeopleInSpaceLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.PersonInSpaceDao
import com.radustavila.nasaexplorer.data.launches.local.RoomLaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.RoomPeopleInSpaceLocalDataSource
import com.radustavila.nasaexplorer.data.launches.reminder.DataStoreLaunchReminderRepository
import com.radustavila.nasaexplorer.data.launches.reminder.WorkManagerLaunchReminderScheduler
import com.radustavila.nasaexplorer.data.launches.remote.LaunchLibraryService
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.remote.RetrofitLaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.repository.LaunchRepositoryImpl
import com.radustavila.nasaexplorer.data.launches.repository.PeopleInSpaceRepositoryImpl
import com.radustavila.nasaexplorer.data.launches.search.LaunchSearchableSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LaunchLibraryApi

/** The DataStore holding the launch reminders the user turned on. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LaunchRemindersStore

@Module
@InstallIn(SingletonComponent::class)
interface LaunchesDataModule {

    @Binds
    fun bindLaunchRepository(impl: LaunchRepositoryImpl): LaunchRepository

    @Binds
    fun bindLaunchLocalDataSource(impl: RoomLaunchLocalDataSource): LaunchLocalDataSource

    @Binds
    fun bindLaunchRemoteDataSource(impl: RetrofitLaunchRemoteDataSource): LaunchRemoteDataSource

    @Binds
    fun bindPeopleInSpaceRepository(impl: PeopleInSpaceRepositoryImpl): PeopleInSpaceRepository

    @Binds
    fun bindPeopleInSpaceLocalDataSource(impl: RoomPeopleInSpaceLocalDataSource): PeopleInSpaceLocalDataSource

    @Binds
    @IntoSet
    fun bindLaunchSearchableSource(impl: LaunchSearchableSource): SearchableSource

    @Binds
    fun bindLaunchReminderRepository(impl: DataStoreLaunchReminderRepository): LaunchReminderRepository

    @Binds
    fun bindLaunchReminderScheduler(impl: WorkManagerLaunchReminderScheduler): LaunchReminderScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object LaunchesInfrastructureModule {

    /** Backed up with the user's other choices (see the app's backup rules). */
    const val REMINDERS_STORE_NAME = "launch_reminders"

    @Provides
    @Singleton
    fun provideLaunchesDatabase(@ApplicationContext context: Context): LaunchesDatabase =
        Room.databaseBuilder(context, LaunchesDatabase::class.java, LaunchesDatabase.NAME)
            // From 4 on Room migrates (see LaunchesDatabase); older versions, or a downgrade, are
            // rebuilt, which costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideLaunchDao(database: LaunchesDatabase): LaunchDao = database.launchDao()

    @Provides
    fun providePersonInSpaceDao(database: LaunchesDatabase): PersonInSpaceDao = database.personInSpaceDao()

    @Provides
    @Singleton
    @LaunchLibraryApi
    fun provideLaunchLibraryRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.LAUNCH_LIBRARY_BASE_URL)

    /**
     * `:core:network` already budgets the production host; this covers the dev host debug builds
     * use, so development traffic is held to the same 15 calls an hour.
     */
    @Provides
    @IntoSet
    fun provideLaunchLibraryRateBudget(): HostRateBudget =
        HostRateBudget.LAUNCH_LIBRARY.copy(host = URI(BuildConfig.LAUNCH_LIBRARY_BASE_URL).host)

    @Provides
    @Singleton
    @LaunchRemindersStore
    fun provideLaunchRemindersDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(REMINDERS_STORE_NAME) },
    )

    @Provides
    @Singleton
    fun provideLaunchLibraryService(@LaunchLibraryApi retrofit: Retrofit): LaunchLibraryService =
        retrofit.create()
}
