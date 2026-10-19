package com.radustavila.nasaexplorer.core.domain.model

import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Instant

/**
 * What the app remembers about synchronising one [SyncSource].
 *
 * @property lastErrorDescription the last failure in readable form; kept after a later success so
 * the Debug screen can still show what went wrong last time (compare the two timestamps).
 * @property nextAllowedRefreshAt before this instant the source must not be called again: either
 * its own refresh interval or a rate-limit cooldown. `null` means "whenever".
 */
data class SourceSyncStatus(
    val source: SyncSource,
    val lastSuccessAt: Instant? = null,
    val lastFailureAt: Instant? = null,
    val lastErrorDescription: String? = null,
    val nextAllowedRefreshAt: Instant? = null,
) {
    val hasRecord: Boolean get() = lastSuccessAt != null || lastFailureAt != null

    /** Whether the most recent attempt failed. */
    val isFailing: Boolean
        get() = lastFailureAt != null && (lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt))

    fun isRefreshDue(now: Instant): Boolean = nextAllowedRefreshAt?.let { !now.isBefore(it) } ?: true
}
