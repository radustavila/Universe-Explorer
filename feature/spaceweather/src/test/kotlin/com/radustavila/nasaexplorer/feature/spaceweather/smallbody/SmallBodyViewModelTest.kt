package com.radustavila.nasaexplorer.feature.spaceweather.smallbody

import androidx.lifecycle.SavedStateHandle
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchSmallBodyDetailsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCloseApproachUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSmallBodyDetailsUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestSmallBodies
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import com.radustavila.nasaexplorer.feature.spaceweather.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric: `toRoute` reads the route through Android's Bundle. */
@RunWith(RobolectricTestRunner::class)
class SmallBodyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSmallBodiesRepository().apply {
        closeApproaches.value = listOf(TestSmallBodies.closeApproach("2025 UK9", sentry = TestSmallBodies.sentry("2025 UK9")))
    }

    private fun createViewModel() = SmallBodyViewModel(
        savedStateHandle = SavedStateHandle(mapOf("designation" to "2025 UK9")),
        observeApproach = ObserveCloseApproachUseCase(repository),
        observeDetails = ObserveSmallBodyDetailsUseCase(repository),
        fetchDetails = FetchSmallBodyDetailsUseCase(repository),
    )

    @Test
    fun `details are fetched once, then come from the cache`() = runTest {
        repository.detailsOnFetch = mapOf("2025 UK9" to TestSmallBodies.details("2025 UK9"))

        val first = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.uiState.collect {} }
        val second = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { second.uiState.collect {} }

        assertEquals(listOf("2025 UK9"), repository.fetchedDesignations)
        assertEquals("Apollo", second.uiState.value.details?.orbitClass)
        assertNotNull(second.uiState.value.approach?.sentry)
    }

    @Test
    fun `a failed lookup can be retried`() = runTest {
        repository.fetchError = AppError.NoConnection
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        // Not the shared "showing cached content" text: there is nothing cached for this object.
        assertEquals(R.string.small_body_details_offline, viewModel.uiState.value.detailsError?.messageRes)

        repository.fetchError = null
        repository.detailsOnFetch = mapOf("2025 UK9" to TestSmallBodies.details("2025 UK9"))
        viewModel.onRetry()

        assertNull(viewModel.uiState.value.detailsError)
        assertNotNull(viewModel.uiState.value.details)
    }
}
