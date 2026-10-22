package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.DeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [DeepSpaceNetworkRepository] that counts refreshes; a success stores the `*OnRefresh` values. */
class FakeDeepSpaceNetworkRepository : DeepSpaceNetworkRepository {

    val snapshot = MutableStateFlow<DsnSnapshot?>(null)
    val farthest = MutableStateFlow<List<DeepSpaceProbe>>(emptyList())

    var liveResult: DataResult<Unit> = DataResult.Success(Unit)
    var configResult: DataResult<Unit> = DataResult.Success(Unit)
    var distancesResult: DataResult<Int> = DataResult.Success(3)
    var snapshotOnRefresh: DsnSnapshot? = null

    var liveRefreshCount: Int = 0
        private set
    var configRefreshCount: Int = 0
        private set
    var distancesRefreshCount: Int = 0
        private set

    override fun observeSnapshot(): Flow<DsnSnapshot?> = snapshot

    override fun observeFarthestProbes(): Flow<List<DeepSpaceProbe>> = farthest

    override suspend fun refreshLive(): DataResult<Unit> {
        liveRefreshCount++
        if (liveResult is DataResult.Success) snapshotOnRefresh?.let { snapshot.value = it }
        return liveResult
    }

    override suspend fun refreshConfig(): DataResult<Unit> {
        configRefreshCount++
        return configResult
    }

    override suspend fun refreshDistances(): DataResult<Int> {
        distancesRefreshCount++
        return distancesResult
    }
}
