package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.quiz.DailyQuizRules
import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizBank
import com.radustavila.nasaexplorer.core.domain.quiz.QuizClock
import com.radustavila.nasaexplorer.core.domain.quiz.QuizProgressRepository
import com.radustavila.nasaexplorer.core.domain.quiz.QuizProgressRules
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderNotifier
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderRules
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderScheduler
import com.radustavila.nasaexplorer.core.domain.quiz.questionRandom
import com.radustavila.nasaexplorer.core.domain.quiz.withShuffledAnswers
import com.radustavila.nasaexplorer.core.model.DailyQuiz
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizOutcome
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The day's five questions (F28): [DailyQuizRules.BANK_PER_DAY] from the bank and up to
 * [DailyQuizRules.GENERATED_PER_DAY] made from cached data, one per source where possible, topped
 * up from the bank when the data gives fewer. Everything is drawn from a seed made from the date,
 * so every device picks the same bank questions on a given day, and no bank question comes back
 * within [DailyQuizRules.NO_REPEAT_DAYS] (see [DailyQuizRules.bankSlots]).
 */
class DailyQuizUseCase @Inject constructor(
    private val bank: QuizBank,
    private val sources: Set<@JvmSuppressWildcards QuizQuestionSource>,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(date: LocalDate = clock.today()): DailyQuiz {
        val seed = DailyQuizRules.seedOf(date)
        val generated = pickGenerated(generateAll(sources, seed, clock.now()), Random(seed))
        val slots = DailyQuizRules.bankSlots(DailyQuizRules.bankOrder(bank.questions()), date)
        val fromBank = slots
            .take(DailyQuizRules.QUESTIONS_PER_DAY - generated.size)
            .map { it.withShuffledAnswers(questionRandom(seed, it.id)) }
        return DailyQuiz(date = date, questions = (fromBank + generated).shuffled(Random(seed)))
    }

    /** One question per source first, sources in a seeded order, then the rest. */
    private fun pickGenerated(bySource: List<List<QuizQuestion>>, random: Random): List<QuizQuestion> {
        val shuffled = bySource.filter { it.isNotEmpty() }.shuffled(random)
        val firsts = shuffled.map { it.first() }
        val others = shuffled.flatMap { it.drop(1) }.shuffled(random)
        return (firsts + others).distinctBy { it.id }.take(DailyQuizRules.GENERATED_PER_DAY)
    }
}

/**
 * Practice by topic: [count] questions of [topic], bank questions never answered right first,
 * with up to [MAX_GENERATED] made from cached data. Not seeded by the date: every round differs.
 */
class PracticeQuizUseCase @Inject constructor(
    private val bank: QuizBank,
    private val sources: Set<@JvmSuppressWildcards QuizQuestionSource>,
    private val progressRepository: QuizProgressRepository,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(
        topic: QuizTopic,
        count: Int = PRACTICE_LENGTH,
        random: Random = Random(clock.now().toEpochMilli()),
    ): List<QuizQuestion> {
        val answered = progressRepository.progress.first().correctBankIds
        val fitting = sources.filter { topic in it.topics }.toSet()
        val generated = generateAll(fitting, random.nextLong(), clock.now())
            .flatten()
            .filter { it.topic == topic }
            .shuffled(random)
            .take(MAX_GENERATED)
        val (fresh, seen) = bank.questions().filter { it.topic == topic }.partition { it.id !in answered }
        val fromBank = (fresh.shuffled(random) + seen.shuffled(random))
            .take(count - generated.size)
            .map { it.withShuffledAnswers(random) }
        return (fromBank + generated).shuffled(random)
    }

    companion object {
        const val PRACTICE_LENGTH = 10
        const val MAX_GENERATED = 2
    }
}

/** How many questions each topic can offer in practice, from the bank alone (fast, offline). */
class ObserveQuizTopicsUseCase @Inject constructor(
    private val bank: QuizBank,
) {
    suspend operator fun invoke(): Map<QuizTopic, Int> {
        val counts = bank.questions().groupingBy { it.topic }.eachCount()
        return QuizTopic.entries.associateWith { counts[it] ?: 0 }
    }
}

class ObserveQuizProgressUseCase @Inject constructor(
    private val repository: QuizProgressRepository,
) {
    operator fun invoke(): Flow<QuizProgress> = repository.progress
}

/** Today's local date, which the daily quiz and the streak go by. */
class QuizTodayUseCase @Inject constructor(
    private val clock: QuizClock,
) {
    operator fun invoke(): LocalDate = clock.today()
}

/** Records a finished daily quiz: the streak, the scores, the badges it earned. */
class FinishDailyQuizUseCase @Inject constructor(
    private val repository: QuizProgressRepository,
) {
    suspend operator fun invoke(quiz: DailyQuiz, answers: List<Boolean>): QuizOutcome {
        var before = QuizProgress()
        val after = repository.update { progress ->
            before = progress
            QuizProgressRules.afterDaily(progress, quiz.date, quiz.questions, answers)
        }
        return QuizOutcome(
            score = answers.count { it },
            total = answers.size,
            streak = after.streakOn(quiz.date),
            newBadges = after.badges - before.badges,
        )
    }
}

/** Records a finished practice round; it counts for badges but not for the streak. */
class FinishPracticeQuizUseCase @Inject constructor(
    private val repository: QuizProgressRepository,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(topic: QuizTopic, questions: List<QuizQuestion>, answers: List<Boolean>): QuizOutcome {
        var before = QuizProgress()
        val after = repository.update { progress ->
            before = progress
            QuizProgressRules.afterPractice(progress, topic, questions, answers)
        }
        return QuizOutcome(
            score = answers.count { it },
            total = answers.size,
            streak = after.streakOn(clock.today()),
            newBadges = after.badges - before.badges,
        )
    }
}

/**
 * Today's "Did you know" lines from every [FunFactSource], in an order seeded by the date: the
 * card opens on the same fact all day and steps through the others.
 */
class DailyFunFactsUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards FunFactSource>,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(date: LocalDate = clock.today()): List<FunFact> {
        val now = clock.now()
        val facts = coroutineScope {
            sources.sortedBy { it.key }.map { source ->
                async { readSafely { source.facts(now) } }
            }.awaitAll()
        }.flatten().distinctBy { it.id }
        return facts.sortedBy { it.id }.shuffled(Random(DailyQuizRules.seedOf(date)))
    }
}

/** Turns the daily reminder on (next one armed) or off (pending one cancelled). */
class SetQuizReminderEnabledUseCase @Inject constructor(
    private val repository: QuizProgressRepository,
    private val scheduler: QuizReminderScheduler,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.update { it.copy(reminderEnabled = enabled) }
        if (enabled) scheduler.scheduleAt(QuizReminderRules.nextAt(clock.now(), clock.zone())) else scheduler.cancel()
    }
}

/**
 * The reminder's wake-up: posts it when today's quiz is not done yet, then arms tomorrow's. Also
 * run at app start, which re-arms a reminder restored from a backup.
 */
class RemindOfDailyQuizUseCase @Inject constructor(
    private val repository: QuizProgressRepository,
    private val scheduler: QuizReminderScheduler,
    private val notifier: QuizReminderNotifier,
    private val clock: QuizClock,
) {
    suspend operator fun invoke(notify: Boolean = true) {
        val progress = repository.progress.first()
        if (!progress.reminderEnabled) {
            scheduler.cancel()
            return
        }
        val today = clock.today()
        if (notify && progress.dailyResultOn(today) == null) notifier.notifyDailyQuiz(progress.streakOn(today))
        scheduler.scheduleAt(QuizReminderRules.nextAt(clock.now(), clock.zone()))
    }
}

/** Each source's questions, in the sources' key order; a failing or slow source gives none. */
private suspend fun generateAll(sources: Set<QuizQuestionSource>, seed: Long, now: Instant): List<List<QuizQuestion>> =
    coroutineScope {
        sources.sortedBy { it.key }.map { source ->
            async { readSafely { source.questions(questionRandom(seed, source.key), now) } }
        }.awaitAll()
    }

private suspend fun <T> readSafely(block: suspend () -> List<T>): List<T> = try {
    withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) { block() }.orEmpty()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    emptyList()
}

/** Sources read the device only; one that takes longer is skipped rather than holding the quiz. */
private const val SOURCE_TIMEOUT_MILLIS = 3_000L
