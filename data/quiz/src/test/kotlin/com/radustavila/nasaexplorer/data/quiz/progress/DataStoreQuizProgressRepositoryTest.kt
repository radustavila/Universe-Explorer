package com.radustavila.nasaexplorer.data.quiz.progress

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.data.quiz.di.QuizInfrastructureModule
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreQuizProgressRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "${QuizInfrastructureModule.PROGRESS_STORE_NAME}.preferences_pb")
        }
    }
    private val repository by lazy { DataStoreQuizProgressRepository(dataStore) }

    @Test
    fun `a new player has no progress and the reminder off`() = scope.runTest {
        assertEquals(QuizProgress(), repository.progress.first())
    }

    @Test
    fun `progress round-trips`() = scope.runTest {
        val progress = QuizProgress(
            lastDailyDate = LocalDate.of(2026, 10, 23),
            lastDailyAnswers = listOf(true, false, true, true, false),
            streak = 4,
            longestStreak = 9,
            bestScore = 5,
            quizzesCompleted = 21,
            totalCorrect = 80,
            correctBankIds = setOf("sol-001", "exo-004"),
            practicedTopics = setOf(QuizTopic.EARTH, QuizTopic.MISSIONS),
            badges = setOf(QuizBadge.FIRST_QUIZ, QuizBadge.STREAK_7),
            reminderEnabled = true,
        )

        val stored = repository.update { progress }

        assertEquals(progress, stored)
        assertEquals(progress, repository.progress.first())
    }

    @Test
    fun `updates apply to what is stored`() = scope.runTest {
        repository.update { it.copy(totalCorrect = 3) }
        repository.update { it.copy(totalCorrect = it.totalCorrect + 2) }

        assertEquals(5, repository.progress.first().totalCorrect)
    }

    @Test
    fun `values it cannot read are skipped`() = scope.runTest {
        dataStore.edit {
            it[stringPreferencesKey("last_daily_date")] = "not a date"
            it[stringSetPreferencesKey("badges")] = setOf("FIRST_QUIZ", "RETIRED_BADGE")
            it[stringSetPreferencesKey("practiced_topics")] = setOf("EARTH", "ASTROLOGY")
        }

        val progress = repository.progress.first()

        assertEquals(null, progress.lastDailyDate)
        assertEquals(setOf(QuizBadge.FIRST_QUIZ), progress.badges)
        assertEquals(setOf(QuizTopic.EARTH), progress.practicedTopics)
    }
}
