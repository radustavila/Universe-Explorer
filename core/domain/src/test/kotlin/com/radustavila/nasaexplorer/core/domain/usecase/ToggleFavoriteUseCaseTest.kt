package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val repository = FakeApodRepository()
    private val toggleFavorite = ToggleFavoriteUseCase(repository)

    @Test
    fun `toggling flips the stored flag and re-emits the feed`() = runTest {
        val apod = TestApods.image(isFavorite = false)
        repository.setEntries(listOf(apod))

        repository.observeFavorites().test {
            assertTrue(awaitItem().isEmpty())

            toggleFavorite(apod)

            assertEquals(listOf(apod.date), awaitItem().map { it.date })
        }
    }

    @Test
    fun `toggling a favorite removes it`() = runTest {
        val apod = TestApods.image(isFavorite = true)
        repository.setEntries(listOf(apod))

        toggleFavorite(apod)

        repository.observeFavorites().test {
            assertTrue(awaitItem().isEmpty())
        }
    }
}
