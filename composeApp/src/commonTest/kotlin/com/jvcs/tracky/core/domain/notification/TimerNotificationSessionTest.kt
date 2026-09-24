package com.jvcs.tracky.core.domain.notification

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimerNotificationSessionTest {

    private fun session(isRunning: Boolean) =
        TimerNotificationSession(
            project = ProjectRef(id = "p1", title = "Tracky App Redesign", colorArgb = null),
            useLightTextColor = false,
            task = TaskRef(id = "t1", title = "Token refresh"),
            subTask = null,
            elapsed = 2.minutes,
            asOf = Instant.fromEpochMilliseconds(0),
            isRunning = isRunning,
        )

    @Test
    fun aRunningClockKeepsCountingPastTheInstantItWasBuilt() {
        val now = Instant.fromEpochMilliseconds(30.seconds.inWholeMilliseconds)

        assertThat(session(isRunning = true).elapsedAt(now)).isEqualTo(2.minutes + 30.seconds)
    }

    @Test
    fun aPausedClockStaysWhereItWasFrozen() {
        val now = Instant.fromEpochMilliseconds(30.seconds.inWholeMilliseconds)

        assertThat(session(isRunning = false).elapsedAt(now)).isEqualTo(2.minutes)
    }

    @Test
    fun aClockThatWentBackwardsNeverRunsBackwards() {
        val running = session(isRunning = true)

        assertThat(running.elapsedAt(Instant.fromEpochMilliseconds(-5_000))).isEqualTo(2.minutes)
    }
}
