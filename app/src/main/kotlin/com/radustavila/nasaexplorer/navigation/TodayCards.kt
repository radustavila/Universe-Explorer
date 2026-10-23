package com.radustavila.nasaexplorer.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveTodayCardsUseCase
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.feature.dsn.navigateToDsn
import com.radustavila.nasaexplorer.feature.dsn.today.DsnRightNowCard
import com.radustavila.nasaexplorer.feature.earth.EarthTodayCard
import com.radustavila.nasaexplorer.feature.earthevents.RightNowOnEarthCard
import com.radustavila.nasaexplorer.feature.earthevents.navigateToEarthEvents
import com.radustavila.nasaexplorer.feature.events.navigateToSkyEvents
import com.radustavila.nasaexplorer.feature.events.next.NextInTheSkyCard
import com.radustavila.nasaexplorer.feature.launches.NextLaunchesCard
import com.radustavila.nasaexplorer.feature.news.navigateToNews
import com.radustavila.nasaexplorer.feature.news.navigateToNewsItem
import com.radustavila.nasaexplorer.feature.news.today.LatestFromNasaCard
import com.radustavila.nasaexplorer.feature.quiz.navigateToDailyQuiz
import com.radustavila.nasaexplorer.feature.quiz.navigateToQuiz
import com.radustavila.nasaexplorer.feature.quiz.today.DailyQuizCard
import com.radustavila.nasaexplorer.feature.quiz.today.DidYouKnowCard
import com.radustavila.nasaexplorer.feature.search.SearchLauncher
import com.radustavila.nasaexplorer.feature.search.navigateToSearch
import com.radustavila.nasaexplorer.feature.settings.navigateToCustomizeToday
import com.radustavila.nasaexplorer.feature.sky.navigateToSkyTonight
import com.radustavila.nasaexplorer.feature.sky.stargazing.StargazingTodayCard
import com.radustavila.nasaexplorer.feature.sky.today.IssNowCard
import com.radustavila.nasaexplorer.feature.sky.today.SkyTonightSummaryCard
import com.radustavila.nasaexplorer.feature.spaceweather.navigateToSpaceWeather
import com.radustavila.nasaexplorer.feature.spaceweather.today.SpaceWeatherTodayCard
import com.radustavila.nasaexplorer.ui.navigateToTopLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

const val TODAY_CUSTOMIZE_BUTTON_TEST_TAG = "today-customize"

/**
 * The cards the Today tab can show above the APOD feed. The registry for every feature that
 * contributes one: add a [TodayCardId] in `:core:model`, its label in Customize Today
 * (`:feature:settings`), and a single line here calling a composable your feature module exposes.
 *
 * ```
 * TodayCardId.NEXT_LAUNCHES to { NextLaunchesCard() }, // F2
 * ```
 *
 * Which cards show, and in what order, is the user's choice (Customize Today); an id without an
 * entry here is skipped. Cards render their own empty/offline state (or nothing at all), and must
 * not scroll vertically: they sit inside the feed's list.
 */
internal fun todayCards(navController: NavHostController): Map<TodayCardId, @Composable () -> Unit> = mapOf(
    // Opens the Earth tab, whose first screen is Earth today, as if its tab had been tapped.
    TodayCardId.EARTH_TODAY to {
        EarthTodayCard(onClick = { navController.navigateToTopLevel(TopLevelDestination.EARTH) })
    }, // F4
    TodayCardId.EARTH_EVENTS to { RightNowOnEarthCard(onClick = { navController.navigateToEarthEvents() }) }, // F9
    TodayCardId.NEXT_LAUNCHES to { NextLaunchesCard() }, // F2
    TodayCardId.ISS_NOW to { IssNowCard(onClick = { navController.navigateToSkyTonight() }) }, // F5
    TodayCardId.SKY_TONIGHT to { SkyTonightSummaryCard(onClick = { navController.navigateToSkyTonight() }) }, // F5
    TodayCardId.SPACE_WEATHER to { SpaceWeatherTodayCard(onClick = { navController.navigateToSpaceWeather() }) }, // F6
    TodayCardId.DSN_NOW to { DsnRightNowCard(onClick = { navController.navigateToDsn() }) }, // F13
    TodayCardId.SKY_EVENTS to {
        NextInTheSkyCard(
            onOpenCalendar = { navController.navigateToSkyEvents() },
            onEventClick = navController::navigateToSkyEvents,
        )
    }, // F12
    TodayCardId.STARGAZING to { StargazingTodayCard(onClick = { navController.navigateToSkyTonight() }) }, // F11
    TodayCardId.NASA_NEWS to {
        LatestFromNasaCard(
            onSeeAllClick = { navController.navigateToNews() },
            onImageClick = navController::navigateToNewsItem,
        )
    }, // F14
    TodayCardId.DAILY_QUIZ to {
        DailyQuizCard(
            onPlayClick = { navController.navigateToDailyQuiz() },
            onOpenClick = { navController.navigateToQuiz() },
        )
    }, // F28
    TodayCardId.DID_YOU_KNOW to { DidYouKnowCard() }, // F28
)

/**
 * The feed's header slot: universal search, always first, with the button to customize Today
 * beside it, then the user's cards in their order.
 */
internal fun todayHeader(navController: NavHostController): @Composable () -> Unit {
    val cards = todayCards(navController)
    return {
        TodayHeader(
            cards = cards,
            onSearchClick = { navController.navigateToSearch() },
            onCustomizeClick = { navController.navigateToCustomizeToday() },
        )
    }
}

@Composable
internal fun TodayHeader(
    cards: Map<TodayCardId, @Composable () -> Unit>,
    onSearchClick: () -> Unit,
    onCustomizeClick: () -> Unit,
    viewModel: TodayCardsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.cards.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchLauncher(onClick = onSearchClick, modifier = Modifier.weight(1f))
            IconButton(onClick = onCustomizeClick, modifier = Modifier.testTag(TODAY_CUSTOMIZE_BUTTON_TEST_TAG)) {
                Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.today_customize))
            }
        }
        // Null until the choice is read: the cards appear once, in the right order.
        enabled?.forEach { id -> cards[id]?.invoke() }
    }
}

/** The user's choice of Today cards; the cards themselves read their own data. */
@HiltViewModel
class TodayCardsViewModel @Inject constructor(
    observeTodayCards: ObserveTodayCardsUseCase,
) : ViewModel() {

    val cards: StateFlow<List<TodayCardId>?> = observeTodayCards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
