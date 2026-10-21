package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Answers with [isPro] after [latency], or with [error] when one is set; a test can flip any of them
 * between calls. Counts the calls.
 */
class FakeProStatusProvider(var isPro: Boolean = true, var latency: Duration = Duration.ZERO) : ProStatusProvider {

    var error: DataError.Remote? = null

    var calls = 0
        private set

    override suspend fun proStatus(): Result<Boolean, DataError.Remote> {
        calls++
        delay(latency)
        return error?.let { Result.Error(it) } ?: Result.Success(isPro)
    }
}
