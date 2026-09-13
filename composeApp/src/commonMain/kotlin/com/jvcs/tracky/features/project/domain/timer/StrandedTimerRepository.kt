package com.jvcs.tracky.features.project.domain.timer

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * The three answers a stranded timer can get.
 *
 * Every one of them ends with the interval closed or gone and its parked row deleted, so an item
 * resolved once never comes back. Until then it is held, not counted.
 */
interface StrandedTimerRepository {

    /** Emits whenever something is parked or resolved. Oldest detection first. */
    fun observeStrandedTimers(): Flow<List<StrandedTimer>>

    /** Banks the full elapsed span, closing at [StrandedTimer.proposedEndAt]. */
    suspend fun keep(timer: StrandedTimer): EmptyResult<DataError>

    /** Banks [duration] instead, closing at `startedAt + duration`. */
    suspend fun keepWithDuration(timer: StrandedTimer, duration: Duration): EmptyResult<DataError>

    /** Deletes the interval outright — locally and on the server, which already has it. */
    suspend fun discard(timer: StrandedTimer): EmptyResult<DataError>
}
