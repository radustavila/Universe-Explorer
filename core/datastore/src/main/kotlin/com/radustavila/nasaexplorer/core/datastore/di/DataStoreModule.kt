package com.radustavila.nasaexplorer.core.datastore.di

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
import com.radustavila.nasaexplorer.core.datastore.SyncMetadataDataSource
import com.radustavila.nasaexplorer.core.datastore.UserPreferencesDataSource
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
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

/** The DataStore holding the user's choices, as opposed to the unqualified sync bookkeeping one. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserPreferencesStore

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
    )

    @Provides
    @Singleton
    @UserPreferencesStore
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_NAME) },
    )

    private const val DATASTORE_NAME = "sync_metadata"
    private const val USER_PREFERENCES_NAME = "user_preferences"
}

@Module
@InstallIn(SingletonComponent::class)
interface DataStoreBindingModule {

    @Binds
    fun bindSyncMetadataRepository(impl: SyncMetadataDataSource): SyncMetadataRepository

    @Binds
    fun bindUserPreferencesRepository(impl: UserPreferencesDataSource): UserPreferencesRepository
}
