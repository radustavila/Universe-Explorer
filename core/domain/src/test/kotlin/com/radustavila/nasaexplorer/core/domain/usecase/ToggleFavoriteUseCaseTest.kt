package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val repository = FakeApodRepository()
    private val timeProvider = FakeTimeProvider()
    private val toggleFavorite = ToggleFavoriteUseCase(repository.favorites, timeProvider)

    @Test
    fun `toggling saves a snapshot under the APOD source and re-emits the feed`() = runTest {
        val apod = TestApods.image(isFavorite = false)
        repository.setEntries(listOf(apod))

        repository.observeFeed().test {
            assertFalse(awaitItem().single().isFavorite)

            toggleFavorite(apod)

            assertTrue(awaitItem().single().isFavorite)
        }
        assertEquals(
            listOf(
                FavoriteItem(
                    source = FavoriteSource.APOD,
                    itemId = apod.date.toString(),
                    title = apod.title,
                    imageUrl = apod.previewImageUrl,
                    savedAt = timeProvider.currentInstant,
                ),
            ),
            repository.favorites.observeFavorites().first(),
        )
    }

    @Test
    fun `toggling a favorite removes it`() = runTest {
        val apod = TestApods.image(isFavorite = true)
        repository.setEntries(listOf(apod))

        toggleFavorite(apod)

        assertTrue(repository.favorites.observeFavorites().first().isEmpty())
        assertFalse(repository.observeApod(apod.date).first()!!.isFavorite)
    }
}
