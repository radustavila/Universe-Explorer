package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.repository.TodayCardsRepository
import com.radustavila.nasaexplorer.core.model.TodayCardId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodayCardsRepository(initial: List<TodayCardId> = TodayCardId.DEFAULT) : TodayCardsRepository {

    private val cards = MutableStateFlow(initial)

    override val todayCards: Flow<List<TodayCardId>> = cards

    val currentTodayCards: List<TodayCardId> get() = cards.value

    override suspend fun setTodayCards(cards: List<TodayCardId>) {
        this.cards.value = cards
    }
}
