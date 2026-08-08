package com.jvcs.tracky.core.domain.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

interface TimeProvider {
    val nowZoneTimeInUtc: LocalDateTime
    val nowInstant: Instant
}

data object SystemTimeProvider: TimeProvider {
    override val nowZoneTimeInUtc: LocalDateTime
        get() {
            val currentInstant = Clock.System.now()
            val timeZone = TimeZone.UTC
            return currentInstant.toLocalDateTime(timeZone)
        }
    override val nowInstant: Instant
        get() = Clock.System.now()
}