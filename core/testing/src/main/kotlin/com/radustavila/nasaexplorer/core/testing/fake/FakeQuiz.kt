package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizBank
import com.radustavila.nasaexplorer.core.domain.quiz.QuizClock
import com.radustavila.nasaexplorer.core.domain.quiz.QuizProgressRepository
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderNotifier
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderScheduler
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeQuizBank(
    var questions: List<QuizQuestion> = emptyList(),
    var facts: List<FunFact> = emptyList(),
) : QuizBank {
    override suspend fun questions(): List<QuizQuestion> = questions

    override suspend fun facts(): List<FunFact> = facts
}

/**
 * Answers [questions] (or what [generate] makes of the random stream); [failure] is thrown instead
 * when set. [calls] records each request's time.
 */
class FakeQuizQuestionSource(
    override val key: String,
    override val topics: Set<QuizTopic> = QuizTopic.entries.toSet(),
    var questions: List<QuizQuestion> = emptyList(),
    var failure: Exception? = null,
    var generate: ((Random) -> List<QuizQuestion>)? = null,
) : QuizQuestionSource {

    val calls = mutableListOf<Instant>()

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        calls += now
        failure?.let { throw it }
        return generate?.invoke(random) ?: questions
    }
}

class FakeFunFactSource(
    override val key: String,
    var facts: List<FunFact> = emptyList(),
    var failure: Exception? = null,
) : FunFactSource {
    override suspend fun facts(now: Instant): List<FunFact> {
        failure?.let { throw it }
        return facts
    }
}

class FakeQuizProgressRepository(initial: QuizProgress = QuizProgress()) : QuizProgressRepository {

    private val state = MutableStateFlow(initial)

    val current: QuizProgress get() = state.value

    override val progress: Flow<QuizProgress> = state

    override suspend fun update(transform: (QuizProgress) -> QuizProgress): QuizProgress {
        state.update(transform)
        return state.value
    }
}

class FakeQuizClock(
    var instant: Instant = Instant.parse("2026-10-23T10:00:00Z"),
    var zoneId: ZoneId = ZoneId.of("Europe/Bucharest"),
) : QuizClock {
    override fun now(): Instant = instant

    override fun zone(): ZoneId = zoneId
}

class FakeQuizReminderScheduler : QuizReminderScheduler {

    /** The pending wake-up, or null when none is armed. */
    var scheduledAt: Instant? = null
        private set

    var cancelCount: Int = 0
        private set

    override fun scheduleAt(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
        cancelCount++
    }
}

class FakeQuizReminderNotifier : QuizReminderNotifier {

    /** The streak passed with each reminder posted. */
    val notified = mutableListOf<Int>()

    override suspend fun notifyDailyQuiz(streak: Int) {
        notified += streak
    }
}
