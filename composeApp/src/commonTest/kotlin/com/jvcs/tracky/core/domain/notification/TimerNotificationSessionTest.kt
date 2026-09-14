package com.jvcs.tracky.core.domain.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimerNotificationSessionTest {

    private fun session(isRunning: Boolean) = TimerNotificationSession(
        projectId = "p1",
        projectTitle = "Tracky App Redesign",
        projectColorArgb = null,
        useLightTextColor = false,
        taskTitle = "Token refresh",
        subTaskTitle = null,
        elapsed = 2.minutes,
        asOf = Instant.fromEpochMilliseconds(0),
        isRunning = isRunning
    )

    @Test
    fun aRunningClockKeepsCountingPastTheInstantItWasBuilt() {
        val now = Instant.fromEpochMilliseconds(30.seconds.inWholeMilliseconds)

        assertEquals(2.minutes + 30.seconds, session(isRunning = true).elapsedAt(now))
    }

    @Test
    fun aPausedClockStaysWhereItWasFrozen() {
        val now = Instant.fromEpochMilliseconds(30.seconds.inWholeMilliseconds)

        assertEquals(2.minutes, session(isRunning = false).elapsedAt(now))
    }

    @Test
    fun aClockThatWentBackwardsNeverRunsBackwards() {
        val running = session(isRunning = true)

        assertEquals(2.minutes, running.elapsedAt(Instant.fromEpochMilliseconds(-5_000)))
    }
}
