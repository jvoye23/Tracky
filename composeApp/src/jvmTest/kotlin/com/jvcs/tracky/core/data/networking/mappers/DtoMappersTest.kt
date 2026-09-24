package com.jvcs.tracky.core.data.networking.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.test.Test
import kotlin.time.Instant

internal class DtoMappersTest {

    @Test
    fun aProjectTreeMapsToItsWireShapeLevelByLevel() {
        val dto = project.toProjectDto()
        val taskDto = dto.tasks.orEmpty().single()
        val subTaskDto = taskDto.subTasks.single()

        assertThat(listOf(dto.id, dto.startDateTimeUtc, dto.trashedAt, dto.updatedAt)).isEqualTo(
            listOf("p1", "2026-08-01T09:00:00Z", "2026-08-05T09:00:00Z", "2026-08-03T09:00:00Z"),
        )
        assertThat(listOf(taskDto.id, taskDto.endDateTimeUtc, taskDto.updatedAt)).isEqualTo(
            listOf("t1", "2026-08-02T09:00:00Z", "2026-08-03T09:00:00Z"),
        )
        assertThat(taskDto.intervals.single().parentSessionId).isEqualTo("t1")
        assertThat(listOf(subTaskDto.subTaskId, subTaskDto.parentProjectTaskId)).isEqualTo(listOf("s1", "t1"))
        assertThat(subTaskDto.intervals.single().parentTaskIntervalId).isEqualTo("i1")
        assertThat(subTaskDto.intervals.single().endDateTimeUtc).isEqualTo(null)
    }

    private companion object {
        val start: Instant = Instant.parse("2026-08-01T09:00:00Z")
        val end: Instant = Instant.parse("2026-08-02T09:00:00Z")
        val stamp: Instant = Instant.parse("2026-08-03T09:00:00Z")

        val project =
            Project(
                projectId = "p1",
                title = "Garden",
                description = null,
                colorArgb = null,
                totalDurationMillis = null,
                startDateTimeUtc = start,
                isFinished = false,
                endDateTimeUtc = end,
                trashedAt = Instant.parse("2026-08-05T09:00:00Z"),
                ownUpdatedAt = stamp,
                projectTasks =
                    listOf(
                        ProjectTask(
                            projectTaskId = "t1",
                            title = "Dig beds",
                            description = null,
                            durationMillis = 60_000L,
                            startDateTimeUtc = start,
                            endDateTimeUtc = end,
                            parentProjectId = "p1",
                            isTimerRunning = false,
                            ownUpdatedAt = stamp,
                            intervals = listOf(TaskInterval("i1", "t1", "p1", start, end, 60_000L)),
                            subTasks =
                                listOf(
                                    ProjectSubTask(
                                        projectSubTaskId = "s1",
                                        parentProjectTaskId = "t1",
                                        parentProjectId = "p1",
                                        title = "Edge",
                                        durationMillis = 0L,
                                        isTimerRunning = true,
                                        startDateTimeUtc = start,
                                        subTaskIntervals =
                                            listOf(
                                                SubTaskInterval("si1", "i1", "s1", "p1", start, null, 0L),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
