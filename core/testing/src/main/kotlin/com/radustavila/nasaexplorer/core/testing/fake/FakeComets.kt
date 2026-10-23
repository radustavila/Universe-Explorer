package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [CometRepository] that counts refreshes; a success stores [cometsOnRefresh] when set. */
class FakeCometRepository(initial: List<Comet> = emptyList()) : CometRepository {

    val comets = MutableStateFlow(initial)

    var refreshResult: DataResult<Int> = DataResult.Success(initial.size)
    var cometsOnRefresh: List<Comet>? = null

    var refreshCount: Int = 0
        private set

    override fun observeComets(): Flow<List<Comet>> = comets

    override fun observeComet(designation: String): Flow<Comet?> =
        comets.map { list -> list.firstOrNull { it.designation == designation } }

    override suspend fun refresh(): DataResult<Int> {
        refreshCount++
        if (refreshResult is DataResult.Success) cometsOnRefresh?.let { comets.value = it }
        return refreshResult
    }
}

/**
 * [CometSkyCalculator] answering from [answers] by designation, with the observer's place dropped
 * when there is none (as the real one leaves `sky` null); a comet without an answer is "out of date".
 */
class FakeCometSkyCalculator(var answers: Map<String, CometTonight> = emptyMap()) : CometSkyCalculator {

    var lastObserver: GeoCoordinate? = null
        private set

    override fun tonight(comet: Comet, observer: GeoCoordinate?, now: Instant): CometTonight? {
        lastObserver = observer
        val answer = answers[comet.designation] ?: return null
        return answer.copy(comet = comet, sky = answer.sky.takeIf { observer != null })
    }
}
