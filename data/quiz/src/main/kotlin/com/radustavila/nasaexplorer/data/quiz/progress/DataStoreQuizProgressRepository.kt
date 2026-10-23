package com.radustavila.nasaexplorer.data.quiz.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.quiz.QuizProgressRepository
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.data.quiz.di.QuizProgressStore
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The quiz progress as plain preferences: dates as ISO text, the last daily quiz's answers as
 * "1"/"0" characters, badges and topics by name. Values it cannot read (a badge dropped in a
 * later version) are skipped.
 */
@Singleton
class DataStoreQuizProgressRepository @Inject constructor(
    @QuizProgressStore private val dataStore: DataStore<Preferences>,
) : QuizProgressRepository {

    override val progress: Flow<QuizProgress> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toProgress() }
        .distinctUntilChanged()

    override suspend fun update(transform: (QuizProgress) -> QuizProgress): QuizProgress {
        var stored: QuizProgress? = null
        try {
            dataStore.edit { prefs ->
                val next = transform(prefs.toProgress())
                prefs.write(next)
                stored = next
            }
        } catch (_: IOException) {
            // Keeps what was stored before; the next quiz writes again.
        }
        return stored ?: progress.first()
    }

    private fun Preferences.toProgress() = QuizProgress(
        lastDailyDate = this[LAST_DAILY_DATE]?.toLocalDateOrNull(),
        lastDailyAnswers = this[LAST_DAILY_ANSWERS].orEmpty().map { it == RIGHT },
        streak = this[STREAK] ?: 0,
        longestStreak = this[LONGEST_STREAK] ?: 0,
        bestScore = this[BEST_SCORE] ?: 0,
        quizzesCompleted = this[QUIZZES_COMPLETED] ?: 0,
        totalCorrect = this[TOTAL_CORRECT] ?: 0,
        correctBankIds = this[CORRECT_BANK_IDS].orEmpty(),
        practicedTopics = this[PRACTICED_TOPICS].orEmpty().mapNotNullTo(mutableSetOf(), QuizTopic::fromName),
        badges = this[BADGES].orEmpty().mapNotNullTo(mutableSetOf()) { name ->
            QuizBadge.entries.firstOrNull { it.name == name }
        },
        reminderEnabled = this[REMINDER_ENABLED] ?: false,
    )

    private fun MutablePreferences.write(progress: QuizProgress) {
        val lastDate = progress.lastDailyDate
        if (lastDate == null) remove(LAST_DAILY_DATE) else this[LAST_DAILY_DATE] = lastDate.toString()
        this[LAST_DAILY_ANSWERS] = String(progress.lastDailyAnswers.map { if (it) RIGHT else WRONG }.toCharArray())
        this[STREAK] = progress.streak
        this[LONGEST_STREAK] = progress.longestStreak
        this[BEST_SCORE] = progress.bestScore
        this[QUIZZES_COMPLETED] = progress.quizzesCompleted
        this[TOTAL_CORRECT] = progress.totalCorrect
        this[CORRECT_BANK_IDS] = progress.correctBankIds
        this[PRACTICED_TOPICS] = progress.practicedTopics.mapTo(mutableSetOf()) { it.name }
        this[BADGES] = progress.badges.mapTo(mutableSetOf()) { it.name }
        this[REMINDER_ENABLED] = progress.reminderEnabled
    }

    private fun String.toLocalDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        val LAST_DAILY_DATE = stringPreferencesKey("last_daily_date")
        val LAST_DAILY_ANSWERS = stringPreferencesKey("last_daily_answers")
        val STREAK = intPreferencesKey("streak")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val BEST_SCORE = intPreferencesKey("best_score")
        val QUIZZES_COMPLETED = intPreferencesKey("quizzes_completed")
        val TOTAL_CORRECT = intPreferencesKey("total_correct")
        val CORRECT_BANK_IDS = stringSetPreferencesKey("correct_bank_ids")
        val PRACTICED_TOPICS = stringSetPreferencesKey("practiced_topics")
        val BADGES = stringSetPreferencesKey("badges")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        const val RIGHT = '1'
        const val WRONG = '0'
    }
}
