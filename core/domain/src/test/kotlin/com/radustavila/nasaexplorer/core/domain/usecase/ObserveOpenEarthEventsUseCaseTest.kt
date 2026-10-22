package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthEventRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveOpenEarthEventsUseCaseTest {

    private val repository = FakeEarthEventRepository()
    private val useCase = ObserveOpenEarthEventsUseCase(
        repository,
        FakeTimeProvider(currentDate = LocalDate.of(2026, 10, 19)),
    )

    private fun wildfire(id: String, daysAgo: Long) =
        TestEarthEvents.wildfire(id = id, at = TestEarthEvents.NOW.minus(Duration.ofDays(daysAgo)))

    @Test
    fun `wildfires quiet for two weeks need the history, for six weeks are gone`() = runTest {
        val storm = TestEarthEvents.storm()
        val volcano = TestEarthEvents.volcano(at = TestEarthEvents.NOW.minus(Duration.ofDays(50)))
        val wildfires = listOf(wildfire("14", 14), wildfire("15", 15), wildfire("42", 42), wildfire("43", 43))
        repository.setEvents(wildfires + storm + volcano)

        assertEquals(listOf(storm.id, "14", volcano.id), useCase().first().map { it.id })
        assertEquals(
            listOf(storm.id, "14", "15", "42", volcano.id),
            useCase(includeHistory = true).first().map { it.id },
        )
    }
}
