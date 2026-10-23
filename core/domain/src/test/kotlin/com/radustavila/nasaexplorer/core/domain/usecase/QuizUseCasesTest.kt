package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.quiz.DailyQuizRules
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizQuestionOrigin
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.testing.data.TestQuiz
import com.radustavila.nasaexplorer.core.testing.fake.FakeFunFactSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizBank
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizClock
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizProgressRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizQuestionSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizReminderNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizReminderScheduler
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizUseCasesTest {

    private val clock = FakeQuizClock()
    private val bank = FakeQuizBank(questions = TestQuiz.bank(300))
    private val date = LocalDate.of(2026, 10, 23)

    private fun source(key: String, count: Int, topic: QuizTopic = QuizTopic.MISSIONS) = FakeQuizQuestionSource(
        key = key,
        topics = setOf(topic),
        generate = { random ->
            // Like a real generator: what it picks depends on the random stream it is given.
            (0 until count).map { TestQuiz.generatedQuestion(id = "$key-${random.nextInt(1_000)}", topic = topic) }
        },
    )

    private fun daily(vararg sources: QuizQuestionSource, quizBank: FakeQuizBank = bank) =
        DailyQuizUseCase(quizBank, sources.toSet(), clock)

    @Test
    fun `a day's quiz has two bank questions and three made from data`() = runTest {
        val quiz = daily(source("a", 2), source("b", 2), source("c", 2))(date)

        assertEquals(5, quiz.questions.size)
        assertEquals(2, quiz.questions.count { it.origin == QuizQuestionOrigin.BANK })
        // One from each source before a second from any.
        val generated = quiz.questions.filter { it.origin == QuizQuestionOrigin.GENERATED }
        assertEquals(setOf("a", "b", "c"), generated.map { it.id.substringBefore('-') }.toSet())
    }

    @Test
    fun `the same date gives the same quiz on every device, whatever the sources' and bank's order`() = runTest {
        val first = daily(source("a", 2), source("b", 1))(date)
        val reversedBank = FakeQuizBank(TestQuiz.bank(300).reversed())
        val otherDevice = daily(source("b", 1), source("a", 2), quizBank = reversedBank)(date)

        assertEquals(first, otherDevice)
    }

    @Test
    fun `another date gives other questions`() = runTest {
        val today = daily()(date)
        val tomorrow = daily()(date.plusDays(1))

        assertNotEquals(today.questions.map { it.id }, tomorrow.questions.map { it.id })
    }

    @Test
    fun `the date comes from the device's local calendar`() = runTest {
        // 22:30 UTC is already the 24th in Bucharest.
        clock.instant = Instant.parse("2026-10-23T22:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 24), daily()().date)
    }

    @Test
    fun `no bank question comes back within 30 days`() = runTest {
        val start = LocalDate.of(2026, 1, 1)
        val days = (0 until 730L).map { offset ->
            val day = start.plusDays(offset)
            day to daily()(day).questions.map { it.id }
        }

        days.forEachIndexed { index, (day, ids) ->
            val recent = days.subList(maxOf(0, index - DailyQuizRules.NO_REPEAT_DAYS + 1), index).flatMap { it.second }
            assertTrue("$day repeats ${ids.intersect(recent.toSet())}", ids.none { it in recent })
        }
    }

    @Test
    fun `fill-up questions do not repeat within 30 days either`() = runTest {
        val start = LocalDate.of(2026, 1, 1)
        val days = (0 until 365L).map { start.plusDays(it) to daily()(start.plusDays(it)).questions.map { q -> q.id } }

        days.forEachIndexed { index, (_, ids) ->
            assertEquals(5, ids.size)
            val recent = days.subList(maxOf(0, index - DailyQuizRules.NO_REPEAT_DAYS + 1), index).flatMap { it.second }
            assertTrue(ids.none { it in recent })
        }
    }

    @Test
    fun `sources without data, failing or too slow give nothing and the quiz still has five questions`() = runTest {
        val empty = FakeQuizQuestionSource(key = "empty")
        val failing = FakeQuizQuestionSource(key = "failing", failure = IllegalStateException("no cache"))
        val slow = object : QuizQuestionSource {
            override val key = "slow"
            override val topics = setOf(QuizTopic.MISSIONS)
            override suspend fun questions(random: Random, now: Instant) = awaitCancellation()
        }

        val quiz = daily(empty, failing, slow, source("one", 1))(date)

        assertEquals(5, quiz.questions.size)
        assertEquals(1, quiz.questions.count { it.origin == QuizQuestionOrigin.GENERATED })
        assertEquals(5, quiz.questions.map { it.id }.distinct().size)
    }

    @Test
    fun `answers are shuffled per day but the right one is still marked`() = runTest {
        val quiz = daily()(date)

        quiz.questions.forEach { question ->
            val right = question.answers[question.correctIndex] as QuizText.Plain
            assertTrue(right.text.startsWith("Right"))
        }
    }

    @Test
    fun `practice asks only the topic, unanswered bank questions first`() = runTest {
        val progress = FakeQuizProgressRepository()
        val solar = TestQuiz.bank(300).filter { it.topic == QuizTopic.SOLAR_SYSTEM }
        progress.update { it.copy(correctBankIds = solar.take(20).map { q -> q.id }.toSet()) }
        val practice = PracticeQuizUseCase(bank, setOf(source("missions", 3, QuizTopic.MISSIONS)), progress, clock)

        val questions = practice(QuizTopic.SOLAR_SYSTEM, random = Random(1))

        assertEquals(10, questions.size)
        assertTrue(questions.all { it.topic == QuizTopic.SOLAR_SYSTEM })
        assertTrue(questions.none { it.id in progress.current.correctBankIds })
    }

    @Test
    fun `practice mixes in questions made from data of that topic`() = runTest {
        val sources = setOf(source("missions", 3, QuizTopic.MISSIONS))
        val practice = PracticeQuizUseCase(bank, sources, FakeQuizProgressRepository(), clock)

        val questions = practice(QuizTopic.MISSIONS, random = Random(2))

        assertEquals(10, questions.size)
        assertEquals(PracticeQuizUseCase.MAX_GENERATED, questions.count { it.origin == QuizQuestionOrigin.GENERATED })
    }

    @Test
    fun `finishing daily quizzes on consecutive days builds a streak, a missed day resets it`() = runTest {
        val progress = FakeQuizProgressRepository()
        val finish = FinishDailyQuizUseCase(progress)
        val quiz = daily()(date)
        val allRight = List(5) { true }

        assertEquals(1, finish(quiz.copy(date = date), allRight).streak)
        assertEquals(2, finish(quiz.copy(date = date.plusDays(1)), allRight).streak)
        val third = finish(quiz.copy(date = date.plusDays(2)), allRight)
        assertEquals(3, third.streak)
        assertTrue(QuizBadge.STREAK_3 in third.newBadges)

        assertEquals(1, finish(quiz.copy(date = date.plusDays(4)), allRight).streak)
        assertEquals(3, progress.current.longestStreak)
        assertEquals(0, progress.current.streakOn(date.plusDays(6)))
    }

    @Test
    fun `first quiz and perfect score badges are earned once, a second finish of the day changes nothing`() = runTest {
        val progress = FakeQuizProgressRepository()
        val finish = FinishDailyQuizUseCase(progress)
        val quiz = daily()(date)

        val outcome = finish(quiz, List(5) { true })
        assertEquals(setOf(QuizBadge.FIRST_QUIZ, QuizBadge.PERFECT_SCORE), outcome.newBadges)
        assertEquals(5, outcome.score)

        val again = finish(quiz, List(5) { false })
        assertEquals(emptySet<QuizBadge>(), again.newBadges)
        assertEquals(1, progress.current.quizzesCompleted)
        assertEquals(List(5) { true }, progress.current.dailyResultOn(date))
        // Bank questions answered right are remembered for practice (no source: all five are the bank's).
        assertEquals(5, progress.current.correctBankIds.size)
    }

    @Test
    fun `practising every topic earns its badge, without touching the streak`() = runTest {
        val progress = FakeQuizProgressRepository()
        val finish = FinishPracticeQuizUseCase(progress, clock)

        val outcomes = QuizTopic.entries.map { topic -> finish(topic, listOf(TestQuiz.bankQuestion()), listOf(true)) }

        assertTrue(QuizBadge.ALL_TOPICS in outcomes.last().newBadges)
        assertEquals(0, progress.current.streak)
        assertNull(progress.current.lastDailyDate)
    }

    @Test
    fun `the day's facts come from every source in an order fixed by the date`() = runTest {
        val bankFacts = FakeFunFactSource("bank", facts = (1..5).map { TestQuiz.fact(id = "fact-$it") })
        val live = FakeFunFactSource("live", facts = listOf(TestQuiz.fact(id = "live-1")))
        val broken = FakeFunFactSource("broken", failure = IllegalStateException())

        val today = DailyFunFactsUseCase(setOf(bankFacts, live, broken), clock)(date)
        val sameDay = DailyFunFactsUseCase(setOf(broken, live, bankFacts), clock)(date)

        assertEquals(6, today.size)
        assertEquals(today, sameDay)
    }

    @Test
    fun `turning the reminder on arms it for 18 00 local time, off cancels it`() = runTest {
        val progress = FakeQuizProgressRepository()
        val scheduler = FakeQuizReminderScheduler()
        val set = SetQuizReminderEnabledUseCase(progress, scheduler, clock)

        set(true)
        // 18:00 in Bucharest (UTC+3 in October) is 15:00 UTC.
        assertEquals(Instant.parse("2026-10-23T15:00:00Z"), scheduler.scheduledAt)
        assertTrue(progress.current.reminderEnabled)

        set(false)
        assertNull(scheduler.scheduledAt)
        assertEquals(false, progress.current.reminderEnabled)
    }

    @Test
    fun `the reminder is posted only while today's quiz is not done, and tomorrow's is armed`() = runTest {
        val progress = FakeQuizProgressRepository(
            QuizProgress(reminderEnabled = true, lastDailyDate = date.minusDays(1), streak = 4),
        )
        val scheduler = FakeQuizReminderScheduler()
        val notifier = FakeQuizReminderNotifier()
        clock.instant = Instant.parse("2026-10-23T15:00:00Z")
        val remind = RemindOfDailyQuizUseCase(progress, scheduler, notifier, clock)

        remind()
        assertEquals(listOf(4), notifier.notified)
        assertEquals(Instant.parse("2026-10-24T15:00:00Z"), scheduler.scheduledAt)

        progress.update { it.copy(lastDailyDate = date, lastDailyAnswers = List(5) { true }) }
        remind()
        assertEquals(listOf(4), notifier.notified)
    }

    @Test
    fun `a reminder that is off posts nothing and arms nothing`() = runTest {
        val scheduler = FakeQuizReminderScheduler()
        val notifier = FakeQuizReminderNotifier()

        RemindOfDailyQuizUseCase(FakeQuizProgressRepository(), scheduler, notifier, clock)()

        assertEquals(emptyList<Int>(), notifier.notified)
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `topics are counted from the bank`() = runTest {
        val counts = ObserveQuizTopicsUseCase(bank)()

        assertEquals(QuizTopic.entries.toSet(), counts.keys)
        assertEquals(300, counts.values.sum())
    }
}
