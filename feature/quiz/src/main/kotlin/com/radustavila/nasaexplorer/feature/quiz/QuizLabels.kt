package com.radustavila.nasaexplorer.feature.quiz

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizTopic

/* How the quiz names and pictures topics, badges and Moon phases. */

@StringRes
internal fun moonPhaseName(phase: MoonPhase): Int = when (phase) {
    MoonPhase.NEW_MOON -> R.string.quiz_moon_phase_new_moon
    MoonPhase.WAXING_CRESCENT -> R.string.quiz_moon_phase_waxing_crescent
    MoonPhase.FIRST_QUARTER -> R.string.quiz_moon_phase_first_quarter
    MoonPhase.WAXING_GIBBOUS -> R.string.quiz_moon_phase_waxing_gibbous
    MoonPhase.FULL_MOON -> R.string.quiz_moon_phase_full_moon
    MoonPhase.WANING_GIBBOUS -> R.string.quiz_moon_phase_waning_gibbous
    MoonPhase.LAST_QUARTER -> R.string.quiz_moon_phase_last_quarter
    MoonPhase.WANING_CRESCENT -> R.string.quiz_moon_phase_waning_crescent
}

@StringRes
internal fun QuizTopic.label(): Int = when (this) {
    QuizTopic.SOLAR_SYSTEM -> R.string.quiz_topic_solar_system
    QuizTopic.SUN_AND_STARS -> R.string.quiz_topic_sun_and_stars
    QuizTopic.DEEP_SPACE -> R.string.quiz_topic_deep_space
    QuizTopic.EXOPLANETS -> R.string.quiz_topic_exoplanets
    QuizTopic.HUMAN_SPACEFLIGHT -> R.string.quiz_topic_human_spaceflight
    QuizTopic.MISSIONS -> R.string.quiz_topic_missions
    QuizTopic.EARTH -> R.string.quiz_topic_earth
    QuizTopic.NIGHT_SKY -> R.string.quiz_topic_night_sky
}

internal val QuizTopic.icon: ImageVector
    get() = when (this) {
        QuizTopic.SOLAR_SYSTEM -> Icons.Outlined.Public
        QuizTopic.SUN_AND_STARS -> Icons.Outlined.WbSunny
        QuizTopic.DEEP_SPACE -> Icons.Outlined.AutoAwesome
        QuizTopic.EXOPLANETS -> Icons.Outlined.Explore
        QuizTopic.HUMAN_SPACEFLIGHT -> Icons.Outlined.RocketLaunch
        QuizTopic.MISSIONS -> Icons.Outlined.SatelliteAlt
        QuizTopic.EARTH -> Icons.Outlined.Public
        QuizTopic.NIGHT_SKY -> Icons.Outlined.Bedtime
    }

@StringRes
internal fun QuizBadge.title(): Int = when (this) {
    QuizBadge.FIRST_QUIZ -> R.string.quiz_badge_first_quiz
    QuizBadge.PERFECT_SCORE -> R.string.quiz_badge_perfect_score
    QuizBadge.STREAK_3 -> R.string.quiz_badge_streak_3
    QuizBadge.STREAK_7 -> R.string.quiz_badge_streak_7
    QuizBadge.STREAK_30 -> R.string.quiz_badge_streak_30
    QuizBadge.QUIZZES_10 -> R.string.quiz_badge_quizzes_10
    QuizBadge.QUIZZES_50 -> R.string.quiz_badge_quizzes_50
    QuizBadge.CORRECT_100 -> R.string.quiz_badge_correct_100
    QuizBadge.ALL_TOPICS -> R.string.quiz_badge_all_topics
}

@StringRes
internal fun QuizBadge.description(): Int = when (this) {
    QuizBadge.FIRST_QUIZ -> R.string.quiz_badge_first_quiz_description
    QuizBadge.PERFECT_SCORE -> R.string.quiz_badge_perfect_score_description
    QuizBadge.STREAK_3 -> R.string.quiz_badge_streak_3_description
    QuizBadge.STREAK_7 -> R.string.quiz_badge_streak_7_description
    QuizBadge.STREAK_30 -> R.string.quiz_badge_streak_30_description
    QuizBadge.QUIZZES_10 -> R.string.quiz_badge_quizzes_10_description
    QuizBadge.QUIZZES_50 -> R.string.quiz_badge_quizzes_50_description
    QuizBadge.CORRECT_100 -> R.string.quiz_badge_correct_100_description
    QuizBadge.ALL_TOPICS -> R.string.quiz_badge_all_topics_description
}

internal val QuizBadge.icon: ImageVector
    get() = when (this) {
        QuizBadge.FIRST_QUIZ -> Icons.Outlined.Rocket
        QuizBadge.PERFECT_SCORE -> Icons.Outlined.Star
        QuizBadge.STREAK_3, QuizBadge.STREAK_7, QuizBadge.STREAK_30 -> Icons.Outlined.LocalFireDepartment
        QuizBadge.QUIZZES_10 -> Icons.Outlined.MilitaryTech
        QuizBadge.QUIZZES_50 -> Icons.Outlined.WorkspacePremium
        QuizBadge.CORRECT_100 -> Icons.Outlined.Verified
        QuizBadge.ALL_TOPICS -> Icons.Outlined.Celebration
    }

/** The icon of the "Did you know" card. */
internal val FunFactIcon: ImageVector get() = Icons.Outlined.TipsAndUpdates
