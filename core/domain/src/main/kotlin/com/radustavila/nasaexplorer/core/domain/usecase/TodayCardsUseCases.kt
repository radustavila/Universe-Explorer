package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.TodayCardsRepository
import com.radustavila.nasaexplorer.core.model.TodayCardId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The cards Today shows above the feed, top to bottom. */
class ObserveTodayCardsUseCase @Inject constructor(
    private val repository: TodayCardsRepository,
) {
    operator fun invoke(): Flow<List<TodayCardId>> = repository.todayCards
}

/** Saves the enabled cards in this order; a card listed twice keeps its first place. */
class SetTodayCardsUseCase @Inject constructor(
    private val repository: TodayCardsRepository,
) {
    suspend operator fun invoke(cards: List<TodayCardId>) = repository.setTodayCards(cards.distinct())
}
