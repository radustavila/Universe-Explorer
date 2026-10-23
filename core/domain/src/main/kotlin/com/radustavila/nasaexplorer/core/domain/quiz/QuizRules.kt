package com.radustavila.nasaexplorer.core.domain.quiz

import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizQuestionOrigin
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** The daily quiz's shape and the rules every device applies the same way. */
object DailyQuizRules {

    const val QUESTIONS_PER_DAY = 5
    const val BANK_PER_DAY = 2
    const val GENERATED_PER_DAY = QUESTIONS_PER_DAY - BANK_PER_DAY

    /**
     * Each day owns this many consecutive places in the bank's fixed order: the first
     * [BANK_PER_DAY] always, the rest when the data gives fewer than [GENERATED_PER_DAY] questions.
     * A bank of N questions comes round again after about N / 5 days, so 150 questions or more
     * never repeat within [NO_REPEAT_DAYS].
     */
    const val BANK_SLOTS_PER_DAY = QUESTIONS_PER_DAY

    const val NO_REPEAT_DAYS = 30

    /** Fixes the bank's order. Changing it reshuffles every day's quiz: never change it. */
    private const val BANK_ORDER_SEED = 0x51A7_F28L

    /** The seed of [date]: the same on every device. */
    fun seedOf(date: LocalDate): Long = date.toEpochDay() * SEED_MULTIPLIER + SEED_OFFSET

    /** The bank in the fixed order every device walks through, independent of the file's order. */
    fun bankOrder(bank: List<QuizQuestion>): List<QuizQuestion> =
        bank.distinctBy { it.id }.sortedBy { it.id }.shuffled(Random(BANK_ORDER_SEED))

    /** The bank questions [date] may use, in order of preference. */
    fun bankSlots(order: List<QuizQuestion>, date: LocalDate): List<QuizQuestion> {
        if (order.isEmpty()) return emptyList()
        val first = date.toEpochDay() * BANK_SLOTS_PER_DAY
        return (0 until BANK_SLOTS_PER_DAY)
            .map { offset -> order[Math.floorMod(first + offset, order.size.toLong()).toInt()] }
            .distinctBy { it.id }
    }

    private const val SEED_MULTIPLIER = 6_364_136_223_846_793_005L
    private const val SEED_OFFSET = 1_442_695_040_888_963_407L
}

/** The answers in an order drawn from [random], with the right one followed. */
fun QuizQuestion.withShuffledAnswers(random: Random): QuizQuestion {
    val order = answers.indices.shuffled(random)
    return copy(answers = order.map { answers[it] }, correctIndex = order.indexOf(correctIndex))
}

/** A random stream for one question of one day, independent of the other questions. */
internal fun questionRandom(seed: Long, id: String): Random = Random(seed xor id.hashCode().toLong())

/** The badges [progress] has earned, given what was just played. */
object QuizBadgeRules {

    fun earned(progress: QuizProgress, perfectDaily: Boolean): Set<QuizBadge> = buildSet {
        addAll(progress.badges)
        if (progress.quizzesCompleted >= 1) add(QuizBadge.FIRST_QUIZ)
        if (perfectDaily) add(QuizBadge.PERFECT_SCORE)
        if (progress.longestStreak >= STREAK_SHORT) add(QuizBadge.STREAK_3)
        if (progress.longestStreak >= STREAK_WEEK) add(QuizBadge.STREAK_7)
        if (progress.longestStreak >= STREAK_MONTH) add(QuizBadge.STREAK_30)
        if (progress.quizzesCompleted >= QUIZZES_FEW) add(QuizBadge.QUIZZES_10)
        if (progress.quizzesCompleted >= QUIZZES_MANY) add(QuizBadge.QUIZZES_50)
        if (progress.totalCorrect >= CORRECT_MANY) add(QuizBadge.CORRECT_100)
        if (progress.practicedTopics.containsAll(QuizTopic.entries)) add(QuizBadge.ALL_TOPICS)
    }

    const val STREAK_SHORT = 3
    const val STREAK_WEEK = 7
    const val STREAK_MONTH = 30
    const val QUIZZES_FEW = 10
    const val QUIZZES_MANY = 50
    const val CORRECT_MANY = 100
}

/** How [progress] changes when a quiz finishes. Pure, so the store applies it atomically. */
object QuizProgressRules {

    fun afterDaily(
        progress: QuizProgress,
        date: LocalDate,
        questions: List<QuizQuestion>,
        answers: List<Boolean>,
    ): QuizProgress {
        // A second finish of the same day (another screen, a retry) changes nothing.
        if (progress.lastDailyDate == date) return progress
        val score = answers.count { it }
        val streak = if (progress.lastDailyDate == date.minusDays(1)) progress.streak + 1 else 1
        val played = progress.copy(
            lastDailyDate = date,
            lastDailyAnswers = answers,
            streak = streak,
            longestStreak = maxOf(progress.longestStreak, streak),
            bestScore = maxOf(progress.bestScore, score),
            quizzesCompleted = progress.quizzesCompleted + 1,
            totalCorrect = progress.totalCorrect + score,
            correctBankIds = progress.correctBankIds + correctBankIds(questions, answers),
        )
        val perfect = answers.size >= DailyQuizRules.QUESTIONS_PER_DAY && score == answers.size
        return played.copy(badges = QuizBadgeRules.earned(played, perfectDaily = perfect))
    }

    fun afterPractice(
        progress: QuizProgress,
        topic: QuizTopic,
        questions: List<QuizQuestion>,
        answers: List<Boolean>,
    ): QuizProgress {
        val played = progress.copy(
            quizzesCompleted = progress.quizzesCompleted + 1,
            totalCorrect = progress.totalCorrect + answers.count { it },
            correctBankIds = progress.correctBankIds + correctBankIds(questions, answers),
            practicedTopics = progress.practicedTopics + topic,
        )
        return played.copy(badges = QuizBadgeRules.earned(played, perfectDaily = false))
    }

    private fun correctBankIds(questions: List<QuizQuestion>, answers: List<Boolean>): Set<String> =
        questions.zip(answers)
            .filter { (question, right) -> right && question.origin == QuizQuestionOrigin.BANK }
            .mapTo(mutableSetOf()) { (question, _) -> question.id }
}

/** The daily reminder fires at [HOUR] local time, if today's quiz is not done by then. */
object QuizReminderRules {

    const val HOUR = 18

    /** The first reminder time strictly after [now]. */
    fun nextAt(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(LocalTime.of(HOUR, 0)).atZone(zone)
        return (if (today.toInstant().isAfter(now)) today else today.plusDays(1)).toInstant()
    }
}
