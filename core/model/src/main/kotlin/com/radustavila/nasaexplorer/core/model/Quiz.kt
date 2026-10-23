package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * What a quiz question is about, for practice by topic (F28). Stored by [name] (the bank's JSON,
 * the progress store), so an entry must never be renamed.
 */
enum class QuizTopic {
    SOLAR_SYSTEM,
    SUN_AND_STARS,
    DEEP_SPACE,
    EXOPLANETS,
    HUMAN_SPACEFLIGHT,
    MISSIONS,
    EARTH,
    NIGHT_SKY,
    ;

    companion object {
        fun fromName(name: String?): QuizTopic? = entries.firstOrNull { it.name == name }
    }
}

enum class QuizDifficulty {
    EASY,
    MEDIUM,
    HARD,
    ;

    companion object {
        fun fromName(name: String?): QuizDifficulty? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Words a question, an answer, an explanation or a fact shows. The bank's are data in its own
 * language ([Plain]); questions made from the app's own data are a [Phrase] the UI words with
 * string resources, so no English is written in Kotlin.
 */
sealed interface QuizText {

    /** Shown as it is: the bank's wording, or a name from the data (a planet, a spacecraft). */
    data class Plain(val text: String) : QuizText

    data class Phrase(val phrase: QuizPhrase, val args: List<QuizArg> = emptyList()) : QuizText {
        constructor(phrase: QuizPhrase, vararg args: QuizArg) : this(phrase, args.toList())
    }
}

/** A value inside a [QuizText.Phrase], formatted by the UI (units, plurals, dates). */
sealed interface QuizArg {
    /** A name from the data, shown as it is. */
    data class Text(val value: String) : QuizArg

    /** A whole number; the first one picks the plural of the phrase. */
    data class Count(val value: Int) : QuizArg

    /** A distance far too large to read in digits, e.g. a spacecraft's: "24.9 billion km". */
    data class LargeDistanceKm(val km: Double) : QuizArg

    /** Light-years are astronomical units, never converted. */
    data class LightYears(val value: Double) : QuizArg

    /** A planet's radius in Earth radii; astronomical, never converted. */
    data class EarthRadii(val value: Double) : QuizArg

    /** An apparent magnitude: lower is brighter. */
    data class Magnitude(val value: Double) : QuizArg

    /** A fraction from 0 to 1, shown as a percentage. */
    data class Percent(val fraction: Double) : QuizArg

    /** An instant within the coming days, shown as the local day and time. */
    data class DayAndTime(val at: Instant) : QuizArg

    /** A day, shown without the time. */
    data class Day(val at: Instant) : QuizArg

    data class PlanetName(val planet: Planet) : QuizArg

    data class MoonPhaseName(val phase: MoonPhase) : QuizArg

    /** A CO₂ concentration in parts per million (F26). */
    data class Ppm(val value: Double) : QuizArg

    /** A temperature change or anomaly in °C, shown signed in the user's degrees. */
    data class TemperatureChange(val celsius: Double) : QuizArg

    /** A small height in millimetres, e.g. how much the sea has risen. */
    data class Millimetres(val mm: Double) : QuizArg

    /** An area in million km², e.g. the Arctic sea ice. */
    data class MillionSquareKm(val value: Double) : QuizArg

    /** The month of a monthly value: "Sep 2026". */
    data class Month(val date: LocalDate) : QuizArg
}

/**
 * Every sentence the app words itself in a quiz or a fact. Each one has a string resource in
 * `:feature:quiz`; [QuizText.Phrase.args] fill its placeholders in order.
 */
enum class QuizPhrase {
    // Answers.
    ANSWER_PLANET,
    ANSWER_MOON_PHASE,
    ANSWER_COUNT,
    ANSWER_HUBBLE,
    ANSWER_WEBB,

    // Exoplanets (F8).
    EXOPLANET_CLOSER_QUESTION,
    EXOPLANET_CLOSER_EXPLANATION,
    EXOPLANET_BIGGER_QUESTION,
    EXOPLANET_BIGGER_EXPLANATION,

    // Universe gallery (F3).
    HUBBLE_OR_WEBB_QUESTION,
    HUBBLE_OR_WEBB_EXPLANATION_HUBBLE,
    HUBBLE_OR_WEBB_EXPLANATION_WEBB,

    // Sky tonight (F5).
    PLANET_UP_TONIGHT_QUESTION,
    PLANET_UP_TONIGHT_EXPLANATION,
    MOON_PHASE_QUESTION,
    MOON_PHASE_EXPLANATION,
    PEOPLE_IN_SPACE_QUESTION,
    PEOPLE_IN_SPACE_EXPLANATION,

    // Deep Space Network (F13).
    FARTHEST_SPACECRAFT_QUESTION,
    FARTHEST_SPACECRAFT_EXPLANATION,

    // Comets (F18).
    BRIGHTEST_COMET_QUESTION,
    BRIGHTEST_COMET_EXPLANATION,

    // Launches (F2).
    NEXT_LAUNCH_QUESTION,
    NEXT_LAUNCH_EXPLANATION,

    // Past launches (F27).
    ANSWER_YES,
    ANSWER_NO,
    BOOSTER_LANDED_QUESTION,
    BOOSTER_LANDED_EXPLANATION_YES,
    BOOSTER_LANDED_EXPLANATION_NO,

    // Climate vital signs (F26).
    ANSWER_VALUE,
    CLIMATE_CO2_QUESTION,
    CLIMATE_CO2_EXPLANATION,
    CLIMATE_TEMPERATURE_QUESTION,
    CLIMATE_TEMPERATURE_EXPLANATION,
    FACT_CLIMATE_CO2,
    FACT_CLIMATE_SEA_ICE,
    FACT_CLIMATE_SEA_LEVEL,

    // Live facts.
    FACT_SPACECRAFT_DISTANCE,
    FACT_PEOPLE_IN_SPACE,
    FACT_CONFIRMED_EXOPLANETS,
    FACT_NEXT_FULL_MOON,
}

/** Where a question comes from: the daily quiz takes two from the bank and three made from data. */
enum class QuizQuestionOrigin {
    BANK,
    GENERATED,
}

/**
 * One multiple-choice question with its explanation.
 *
 * @property id unique and stable: the bank's id, or the generator's (with its data in it) so the
 * same day's question keeps its identity.
 * @property correctIndex index of the right answer in [answers].
 * @property imageUrl a picture the question is about ("Hubble or Webb?"), or null.
 */
data class QuizQuestion(
    val id: String,
    val topic: QuizTopic,
    val difficulty: QuizDifficulty,
    val prompt: QuizText,
    val answers: List<QuizText>,
    val correctIndex: Int,
    val explanation: QuizText,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val origin: QuizQuestionOrigin = QuizQuestionOrigin.GENERATED,
) {
    init {
        require(answers.size in MIN_ANSWERS..MAX_ANSWERS) { "$id: ${answers.size} answers" }
        require(correctIndex in answers.indices) { "$id: correct index $correctIndex" }
    }

    companion object {
        const val MIN_ANSWERS = 2
        const val MAX_ANSWERS = 4
    }
}

/** A "Did you know" line: from the bank, or computed from cached data. */
data class FunFact(
    val id: String,
    val topic: QuizTopic,
    val text: QuizText,
    val sourceUrl: String? = null,
)

/** The day's five questions, the same for everyone on [date] (see `DailyQuizUseCase`). */
data class DailyQuiz(
    val date: LocalDate,
    val questions: List<QuizQuestion>,
)

/** Earned once, kept for good. Stored by [name]: never rename. */
enum class QuizBadge {
    FIRST_QUIZ,
    PERFECT_SCORE,
    STREAK_3,
    STREAK_7,
    STREAK_30,
    QUIZZES_10,
    QUIZZES_50,
    CORRECT_100,
    ALL_TOPICS,
}

/**
 * The player's progress, kept on the device and in the backup.
 *
 * @property lastDailyDate the last day whose daily quiz was finished; the streak counts up to it.
 * @property lastDailyAnswers whether each of that quiz's answers was right, in order.
 * @property streak consecutive days of daily quizzes up to [lastDailyDate].
 * @property correctBankIds bank questions ever answered right; practice asks the others first.
 */
data class QuizProgress(
    val lastDailyDate: LocalDate? = null,
    val lastDailyAnswers: List<Boolean> = emptyList(),
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val bestScore: Int = 0,
    val quizzesCompleted: Int = 0,
    val totalCorrect: Int = 0,
    val correctBankIds: Set<String> = emptySet(),
    val practicedTopics: Set<QuizTopic> = emptySet(),
    val badges: Set<QuizBadge> = emptySet(),
    val reminderEnabled: Boolean = false,
) {
    /** The streak still alive on [today]: it ends once a whole day passes without a daily quiz. */
    fun streakOn(today: LocalDate): Int {
        val last = lastDailyDate ?: return 0
        return if (last == today || last == today.minusDays(1)) streak else 0
    }

    /** Today's result, or null while today's daily quiz is not done. */
    fun dailyResultOn(today: LocalDate): List<Boolean>? = lastDailyAnswers.takeIf { lastDailyDate == today }
}

/** What finishing a quiz changed, for the result screen. */
data class QuizOutcome(
    val score: Int,
    val total: Int,
    val streak: Int,
    val newBadges: Set<QuizBadge>,
)

/** In-app address of the quiz, opened by its daily reminder. */
object QuizDeepLink {
    const val URI = "nasaexplorer://quiz"
}
