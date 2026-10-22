package com.radustavila.nasaexplorer.data.skyevents.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.radustavila.nasaexplorer.core.astro.EventCalendar
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventCalendar
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderScheduler
import com.radustavila.nasaexplorer.data.skyevents.reminder.DataStoreSkyEventReminderRepository
import com.radustavila.nasaexplorer.data.skyevents.reminder.WorkManagerSkyEventReminderScheduler
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

/** The DataStore holding the sky event reminders the user turned on. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SkyEventRemindersStore

/** What touches the device's storage or schedules work; the end-to-end test may swap this module. */
@Module
@InstallIn(SingletonComponent::class)
interface SkyEventsDataModule {

    @Binds
    fun bindSkyEventReminderRepository(impl: DataStoreSkyEventReminderRepository): SkyEventReminderRepository

    @Binds
    fun bindSkyEventReminderScheduler(impl: WorkManagerSkyEventReminderScheduler): SkyEventReminderScheduler
}

/** The calendar: pure computation, the same in tests and production. */
@Module
@InstallIn(SingletonComponent::class)
interface SkyEventCalendarModule {

    @Binds
    fun bindSkyEventCalendar(impl: EventCalendar): SkyEventCalendar
}

@Module
@InstallIn(SingletonComponent::class)
object SkyEventsInfrastructureModule {

    /** Backed up with the user's other choices (see the app's backup rules). */
    const val REMINDERS_STORE_NAME = "sky_event_reminders"

    @Provides
    @Singleton
    @SkyEventRemindersStore
    fun provideSkyEventRemindersDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(REMINDERS_STORE_NAME) },
    )
}
