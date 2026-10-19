package com.radustavila.nasaexplorer.core.location.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.location.CityDirectory
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.location.AndroidDeviceLocationProvider
import com.radustavila.nasaexplorer.core.location.BundledCityDirectory
import com.radustavila.nasaexplorer.core.location.DataStoreObserverLocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/** The DataStore holding the observer's location. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ObserverLocationStore

@Module
@InstallIn(SingletonComponent::class)
interface LocationModule {

    @Binds
    fun bindObserverLocationRepository(impl: DataStoreObserverLocationRepository): ObserverLocationRepository

    @Binds
    fun bindDeviceLocationProvider(impl: AndroidDeviceLocationProvider): DeviceLocationProvider

    @Binds
    fun bindCityDirectory(impl: BundledCityDirectory): CityDirectory
}

@Module
@InstallIn(SingletonComponent::class)
object LocationStoreModule {

    @Provides
    @Singleton
    @ObserverLocationStore
    fun provideObserverLocationDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(OBSERVER_LOCATION_NAME) },
    )

    private const val OBSERVER_LOCATION_NAME = "observer_location"
}
