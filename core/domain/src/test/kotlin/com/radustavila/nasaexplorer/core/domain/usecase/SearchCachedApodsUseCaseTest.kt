package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCachedApodsUseCaseTest {

    private val repository = FakeApodRepository()
    private val search = SearchCachedApodsUseCase(repository)

    @Test
    fun `finds cached entries by words of their title`() = runTest {
        repository.setEntries(
            listOf(
                TestApods.image(date = LocalDate.of(2026, 9, 10), title = "Andromeda Rising"),
                TestApods.image(date = LocalDate.of(2026, 9, 9), title = "Saturn's Rings"),
            ),
        )

        assertEquals(listOf("Andromeda Rising"), search("  androm ").first().map { it.title })
    }

    @Test
    fun `a blank query finds nothing rather than everything`() = runTest {
        repository.setEntries(TestApods.feed(size = 3))

        assertTrue(search("   ").first().isEmpty())
    }
}
