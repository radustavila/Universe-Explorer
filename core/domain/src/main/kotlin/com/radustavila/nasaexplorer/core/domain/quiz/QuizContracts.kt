package com.radustavila.nasaexplorer.core.domain.quiz

import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow

/** The bundled question bank (F28): questions and facts in the device's language, else English. */
interface QuizBank {

    /** Every usable question, in the bank's order; entries it cannot read are left out. */
    suspend fun questions(): List<QuizQuestion>

    suspend fun facts(): List<FunFact>
}

/**
 * Questions made from data the app already holds (exoplanets, the gallery, tonight's sky, the Deep
 * Space Network, comets, launches). Each source is contributed to the Hilt set (`@Binds @IntoSet`),
 * like `SearchableSource`, so a new feature adds its questions with one binding.
 *
 * A source reads the device only, answers quickly, and returns nothing while it has no data: the
 * daily quiz fills up from the bank.
 */
interface QuizQuestionSource {

    /** Stable and unique: part of the daily seed, so the set's order never changes a day's quiz. */
    val key: String

    /** The topics its questions belong to, so practice asks only the sources that fit. */
    val topics: Set<QuizTopic>

    /**
     * A few questions over what is cached at [now], picked and shuffled with [random] only, so the
     * same seed and data give the same questions. Answers come in their final order.
     */
    suspend fun questions(random: Random, now: Instant): List<QuizQuestion>
}

/** "Did you know" lines, from the bank or computed from cached data; contributed like [QuizQuestionSource]. */
interface FunFactSource {

    val key: String

    /** The facts it can tell at [now]; empty without data. */
    suspend fun facts(now: Instant): List<FunFact>
}

/** Streaks, scores, badges and the reminder switch, kept with the user's other choices. */
interface QuizProgressRepository {

    val progress: Flow<QuizProgress>

    /** Applies [transform] atomically and returns what was stored. */
    suspend fun update(transform: (QuizProgress) -> QuizProgress): QuizProgress
}

/** The device's clock and calendar: the daily quiz changes at local midnight, not UTC's. */
interface QuizClock {

    fun now(): Instant

    fun zone(): ZoneId

    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

/** Wakes the app once at [scheduleAt] for the daily reminder; one pending wake-up at a time. */
interface QuizReminderScheduler {

    fun scheduleAt(at: Instant)

    fun cancel()
}

/** Posts "Today's quiz is waiting"; silent when notifications are blocked. */
interface QuizReminderNotifier {

    suspend fun notifyDailyQuiz(streak: Int)
}
