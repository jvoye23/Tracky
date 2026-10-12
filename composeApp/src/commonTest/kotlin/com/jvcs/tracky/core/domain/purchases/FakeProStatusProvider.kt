package com.jvcs.tracky.core.domain.purchases

import kotlinx.coroutines.delay
import kotlin.time.Duration

/** Answers with [isPro] after [latency]; a test can flip either between calls. Counts the calls. */
class FakeProStatusProvider(var isPro: Boolean = true, var latency: Duration = Duration.ZERO) : ProStatusProvider {

    var calls = 0
        private set

    override suspend fun isPro(): Boolean {
        calls++
        delay(latency)
        return isPro
    }
}
