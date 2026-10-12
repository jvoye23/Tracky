package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.delay
import kotlin.time.Duration

/** Answers every refresh with [result] after [latency], and counts the calls. */
class FakeEntitlementService(
    var result: Result<Boolean, DataError.Remote> = Result.Success(true),
    var latency: Duration = Duration.ZERO,
) : EntitlementService {

    var refreshCalls = 0
        private set

    override suspend fun refresh(): Result<Boolean, DataError.Remote> {
        refreshCalls++
        delay(latency)
        return result
    }
}
