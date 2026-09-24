package com.jvcs.tracky.features.project.data.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.TaskWithIntervals
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlin.test.Test
import kotlin.time.Instant

internal class ProjectTaskMapperTest {

    @Test
    fun aTaskSurvivesTheRoundTripThroughItsEntityWithItsIntervals() {
        val interval =
            TaskIntervalEntity(
                intervalId = "i1",
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0L,
                endDateTimeEpochMs = 60_000L,
                durationMillis = 60_000L,
            )

        val mapped = TaskWithIntervals(task.toProjectTaskEntity(), listOf(interval)).toProjectTask()

        assertThat(mapped.copy(intervals = emptyList())).isEqualTo(task)
        assertThat(mapped.intervals.single().intervalId).isEqualTo("i1")
    }

    @Test
    fun anUnknownDurationIsStoredAsZero() {
        assertThat(task.copy(durationMillis = null).toProjectTaskEntity().durationMillis).isEqualTo(0L)
    }

    private companion object {
        val task =
            ProjectTask(
                projectTaskId = "t1",
                title = "Dig beds",
                description = "Before April",
                durationMillis = 60_000L,
                startDateTimeUtc = Instant.parse("2026-08-01T09:00:00Z"),
                endDateTimeUtc = Instant.parse("2026-08-02T09:00:00Z"),
                isFinished = true,
                parentProjectId = "p1",
                isTimerRunning = false,
                ownUpdatedAt = Instant.parse("2026-08-03T09:00:00Z"),
                sortIndex = 2L,
            )
    }
}
