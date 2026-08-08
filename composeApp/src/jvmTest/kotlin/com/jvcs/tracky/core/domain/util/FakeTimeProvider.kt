@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.domain.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A clock tests control. Set [now] to pin the time; set [advanceOnReadMillis] to make every read
 * move the clock forward, which is how a test catches code that reads the clock more than once
 * where a single instant was intended.
 */
internal class FakeTimeProvider(
    var now: Instant = Instant.fromEpochMilliseconds(0),
    private val advanceOnReadMillis: Long = 0
) : TimeProvider {
    override val nowInstant: Instant
        get() = now.also { if (advanceOnReadMillis != 0L) now += advanceOnReadMillis.milliseconds }
    override val nowZoneTimeInUtc: LocalDateTime
        get() = nowInstant.toLocalDateTime(TimeZone.UTC)
}
