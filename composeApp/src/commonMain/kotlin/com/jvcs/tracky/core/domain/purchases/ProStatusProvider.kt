package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result

/**
 * Whether the user may use the Pro features on this device: more than five projects, subtasks,
 * export, archive and pin (Requirements/basic_free_user_flow.md).
 */
interface ProStatusProvider {

    /**
     * Success when the answer is known: true when the store or the server says Pro, false only when
     * every source asked said free. An error when a source that could have said Pro did not answer.
     */
    suspend fun proStatus(): Result<Boolean, DataError.Remote>

    /** True when the store or the server says Pro. Unknown counts as free: the paywall offers Restore. */
    suspend fun isPro(): Boolean = proStatus() == Result.Success(true)
}
