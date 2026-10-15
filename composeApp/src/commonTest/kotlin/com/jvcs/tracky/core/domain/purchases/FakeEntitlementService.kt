package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Answers every refresh with [result] after [latency], and every read of the stored entitlement
 * with [currentResult]. Counts both.
 */
class FakeEntitlementService(
    var result: Result<Boolean, DataError.Remote> = Result.Success(true),
    var latency: Duration = Duration.ZERO,
    var currentResult: Result<Boolean, DataError.Remote> = Result.Success(false),
) : EntitlementService {

    var currentCalls = 0
        private set

    override suspend fun current(): Result<Boolean, DataError.Remote> {
        currentCalls++
        return currentResult
    }

    var refreshCalls = 0
        private set

    override suspend fun refresh(): Result<Boolean, DataError.Remote> {
        refreshCalls++
        delay(latency)
        return result
    }
}
