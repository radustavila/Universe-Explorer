package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.EpicPhotoRef
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleEpicFavoriteUseCaseTest {

    private val favorites = FakeFavoritesRepository()
    private val toggle = ToggleEpicFavoriteUseCase(
        saveFavorite = SaveFavoriteUseCase(favorites, FakeTimeProvider()),
        removeFavorite = RemoveFavoriteUseCase(favorites),
    )

    @Test
    fun `saves a photo under its findable id with its capture time and the full JPEG`() = runTest {
        val image = TestEpicImages.image(index = 1)

        toggle(image, isFavorite = false)

        val saved = favorites.current.single()
        assertEquals(FavoriteSource.EPIC, saved.source)
        assertEquals("natural/2026-10-16/2026101601", saved.itemId)
        // Raw data, not an English sentence: the Favourites tab words it.
        assertEquals("2026-10-16T02:10:00Z", saved.title)
        assertEquals(image.capturedAt, EpicImage.capturedAtOfFavoriteTitle(saved.title))
        assertEquals(image.imageUrl, saved.imageUrl)
        assertEquals(setOf(saved.itemId), ObserveEpicFavoriteIdsUseCase(favorites)().first())
    }

    @Test
    fun `enhanced photos are told apart by their id`() = runTest {
        toggle(TestEpicImages.image(collection = EpicCollection.ENHANCED), isFavorite = false)

        val saved = favorites.current.single()
        assertEquals(EpicCollection.ENHANCED, EpicPhotoRef.fromFavoriteId(saved.itemId)?.collection)
    }

    @Test
    fun `titles saved by older versions are not mistaken for a time`() {
        assertNull(EpicImage.capturedAtOfFavoriteTitle("Earth, 16 Oct 2026, 02:10 UTC"))
    }

    @Test
    fun `toggling a saved photo removes it`() = runTest {
        val image = TestEpicImages.image()
        toggle(image, isFavorite = false)

        toggle(image, isFavorite = true)

        assertTrue(favorites.current.isEmpty())
    }
}
