package com.jvcs.tracky.features.project.domain.timer

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RunningTimerTest {

    private fun timer(startedAt: Instant = Instant.fromEpochMilliseconds(0), bankedDuration: Duration = Duration.ZERO) =
        RunningTimer(
            project = ProjectRef(id = "p1", title = "Tracky App Redesign", colorArgb = null),
            useLightTextColor = false,
            task = TaskRef(id = "t1", title = "Token refresh"),
            subTask = null,
            startedAt = startedAt,
            bankedDuration = bankedDuration,
        )

    @Test
    fun elapsedAddsTheOpenSpanToWhatIsAlreadyBanked() {
        val running = timer(bankedDuration = 2.hours)

        val elapsed = running.elapsedAt(Instant.fromEpochMilliseconds((16 * 60 + 9) * 1000L))

        // The number on the mock: 2h banked plus 16m09s of this sitting.
        assertThat(elapsed).isEqualTo(2.hours + 16.minutes + 9.seconds)
    }

    @Test
    fun aClockThatWentBackwardsNeverEatsIntoTheBankedTotal() {
        val running = timer(startedAt = Instant.fromEpochMilliseconds(10_000), bankedDuration = 1.hours)

        val elapsed = running.elapsedAt(Instant.fromEpochMilliseconds(0))

        assertThat(elapsed).isEqualTo(1.hours)
    }

    @Test
    fun theTimedEntityIsTheSubTaskWhenOneIsRunning() {
        assertThat(timer().timedEntityId).isEqualTo("t1")
        assertThat(timer().copy(subTask = TaskRef(id = "s1", title = "Auth endpoints")).timedEntityId).isEqualTo("s1")
    }
}
