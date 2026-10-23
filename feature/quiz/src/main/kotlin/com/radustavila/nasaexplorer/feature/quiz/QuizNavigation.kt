// Named for the feature, like every other feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.quiz

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.QuizDeepLink
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.feature.quiz.home.QuizHomeScreen
import com.radustavila.nasaexplorer.feature.quiz.play.QuizPlayScreen
import kotlinx.serialization.Serializable

/** The quiz's home: today's quiz, practice by topic, badges, the reminder. */
@Serializable
data object QuizRoute

/**
 * One round: the daily quiz when [topic] is null, else ten practice questions of that topic
 * ([QuizTopic] by name).
 */
@Serializable
data class QuizPlayRoute(val topic: String? = null)

fun NavController.navigateToQuiz(navOptions: NavOptions? = null) = navigate(QuizRoute, navOptions)

fun NavController.navigateToDailyQuiz(navOptions: NavOptions? = null) = navigate(QuizPlayRoute(), navOptions)

fun NavController.navigateToPractice(topic: QuizTopic, navOptions: NavOptions? = null) =
    navigate(QuizPlayRoute(topic.name), navOptions)

/**
 * The quiz screens, registered in the More tab's graph; the daily reminder opens [QuizRoute] through
 * [QuizDeepLink].
 */
fun NavGraphBuilder.quizScreens(navController: NavController) {
    composable<QuizRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = QuizDeepLink.URI }),
    ) {
        QuizHomeScreen(
            onBackClick = navController::popBackStack,
            onDailyClick = { navController.navigateToDailyQuiz() },
            onTopicClick = { navController.navigateToPractice(it) },
        )
    }
    composable<QuizPlayRoute> {
        QuizPlayScreen(
            onCloseClick = navController::popBackStack,
            onPracticeAgain = { topic ->
                navController.popBackStack()
                navController.navigateToPractice(topic)
            },
        )
    }
}
