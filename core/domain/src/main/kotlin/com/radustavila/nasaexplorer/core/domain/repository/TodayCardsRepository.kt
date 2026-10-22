package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.model.TodayCardId
import kotlinx.coroutines.flow.Flow

/** Which cards Today shows above the feed, and in what order: a choice the user makes. */
interface TodayCardsRepository {

    /** The enabled cards, top to bottom, without duplicates; [TodayCardId.DEFAULT] until the user picks. */
    val todayCards: Flow<List<TodayCardId>>

    /** Saves [cards] as the enabled cards, in this order. An empty list hides every card. */
    suspend fun setTodayCards(cards: List<TodayCardId>)
}
