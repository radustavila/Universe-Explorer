package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.core.testing.fake.FakeTodayCardsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCardsUseCasesTest {

    private val repository = FakeTodayCardsRepository()

    @Test
    fun `Today starts with the default cards and follows every save`() = runTest {
        ObserveTodayCardsUseCase(repository)().test {
            assertEquals(TodayCardId.DEFAULT, awaitItem())

            SetTodayCardsUseCase(repository)(listOf(TodayCardId.ISS_NOW))
            assertEquals(listOf(TodayCardId.ISS_NOW), awaitItem())
        }
    }

    @Test
    fun `a card listed twice keeps its first place`() = runTest {
        SetTodayCardsUseCase(repository)(
            listOf(TodayCardId.SKY_TONIGHT, TodayCardId.EARTH_TODAY, TodayCardId.SKY_TONIGHT),
        )

        assertEquals(listOf(TodayCardId.SKY_TONIGHT, TodayCardId.EARTH_TODAY), repository.currentTodayCards)
    }
}
