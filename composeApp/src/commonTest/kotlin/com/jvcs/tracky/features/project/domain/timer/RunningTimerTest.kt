package com.jvcs.tracky.features.project.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RunningTimerTest {

    private fun timer(
        startedAt: Instant = Instant.fromEpochMilliseconds(0),
        bankedDuration: Duration = Duration.ZERO
    ) = RunningTimer(
        projectId = "p1",
        projectTitle = "Tracky App Redesign",
        projectColorArgb = null,
        useLightTextColor = false,
        taskId = "t1",
        taskTitle = "Token refresh",
        subTaskId = null,
        subTaskTitle = null,
        startedAt = startedAt,
        bankedDuration = bankedDuration
    )

    @Test
    fun elapsedAddsTheOpenSpanToWhatIsAlreadyBanked() {
        val running = timer(bankedDuration = 2.hours)

        val elapsed = running.elapsedAt(Instant.fromEpochMilliseconds((16 * 60 + 9) * 1000L))

        // The number on the mock: 2h banked plus 16m09s of this sitting.
        assertEquals(2.hours + 16.minutes + 9.seconds, elapsed)
    }

    @Test
    fun aClockThatWentBackwardsNeverEatsIntoTheBankedTotal() {
        val running = timer(startedAt = Instant.fromEpochMilliseconds(10_000), bankedDuration = 1.hours)

        val elapsed = running.elapsedAt(Instant.fromEpochMilliseconds(0))

        assertEquals(1.hours, elapsed)
    }

    @Test
    fun theTimedEntityIsTheSubTaskWhenOneIsRunning() {
        assertEquals("t1", timer().timedEntityId)
        assertEquals("s1", timer().copy(subTaskId = "s1").timedEntityId)
    }
}
