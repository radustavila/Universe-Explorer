package com.radustavila.nasaexplorer.data.quiz.di

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
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizBank
import com.radustavila.nasaexplorer.core.domain.quiz.QuizClock
import com.radustavila.nasaexplorer.core.domain.quiz.QuizProgressRepository
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderScheduler
import com.radustavila.nasaexplorer.data.quiz.bank.AssetQuizBank
import com.radustavila.nasaexplorer.data.quiz.bank.BankFunFactSource
import com.radustavila.nasaexplorer.data.quiz.generator.CometQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.DeepSpaceFunFactSource
import com.radustavila.nasaexplorer.data.quiz.generator.DeepSpaceQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.ExoplanetFunFactSource
import com.radustavila.nasaexplorer.data.quiz.generator.ExoplanetQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.GalleryQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.LaunchQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.MoonFunFactSource
import com.radustavila.nasaexplorer.data.quiz.generator.PeopleInSpaceFunFactSource
import com.radustavila.nasaexplorer.data.quiz.generator.PeopleInSpaceQuizSource
import com.radustavila.nasaexplorer.data.quiz.generator.SkyTonightQuizSource
import com.radustavila.nasaexplorer.data.quiz.progress.DataStoreQuizProgressRepository
import com.radustavila.nasaexplorer.data.quiz.reminder.WorkManagerQuizReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Instant
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/** The DataStore holding the quiz progress and the reminder switch. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class QuizProgressStore

/** What touches the device's storage or schedules work; the end-to-end test may swap this module. */
@Module
@InstallIn(SingletonComponent::class)
interface QuizDataModule {

    @Binds
    fun bindQuizBank(impl: AssetQuizBank): QuizBank

    @Binds
    fun bindQuizProgressRepository(impl: DataStoreQuizProgressRepository): QuizProgressRepository

    @Binds
    fun bindQuizReminderScheduler(impl: WorkManagerQuizReminderScheduler): QuizReminderScheduler
}

/**
 * The question sources made from other features' cached data. A later feature adds its own the
 * same way, one `@IntoSet` binding in its own module.
 */
@Module
@InstallIn(SingletonComponent::class)
interface QuizQuestionSourcesModule {

    @Binds
    @IntoSet
    fun bindExoplanetQuizSource(impl: ExoplanetQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindGalleryQuizSource(impl: GalleryQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindSkyTonightQuizSource(impl: SkyTonightQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindPeopleInSpaceQuizSource(impl: PeopleInSpaceQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindDeepSpaceQuizSource(impl: DeepSpaceQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindCometQuizSource(impl: CometQuizSource): QuizQuestionSource

    @Binds
    @IntoSet
    fun bindLaunchQuizSource(impl: LaunchQuizSource): QuizQuestionSource

}

/** The "Did you know" sources: the bank's facts and live ones from cached data. */
@Module
@InstallIn(SingletonComponent::class)
interface FunFactSourcesModule {

    @Binds
    @IntoSet
    fun bindBankFunFactSource(impl: BankFunFactSource): FunFactSource

    @Binds
    @IntoSet
    fun bindExoplanetFunFactSource(impl: ExoplanetFunFactSource): FunFactSource

    @Binds
    @IntoSet
    fun bindPeopleInSpaceFunFactSource(impl: PeopleInSpaceFunFactSource): FunFactSource

    @Binds
    @IntoSet
    fun bindDeepSpaceFunFactSource(impl: DeepSpaceFunFactSource): FunFactSource

    @Binds
    @IntoSet
    fun bindMoonFunFactSource(impl: MoonFunFactSource): FunFactSource
}

@Module
@InstallIn(SingletonComponent::class)
object QuizInfrastructureModule {

    /** Backed up with the user's other choices (see the app's backup rules). */
    const val PROGRESS_STORE_NAME = "quiz_progress"

    @Provides
    @Singleton
    @QuizProgressStore
    fun provideQuizProgressDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(PROGRESS_STORE_NAME) },
    )

    /** The device's own time zone, read on every call so a trip abroad moves the quiz's day. */
    @Provides
    fun provideQuizClock(timeProvider: TimeProvider): QuizClock = object : QuizClock {
        override fun now(): Instant = timeProvider.now()

        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
