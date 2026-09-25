package com.jvcs.tracky.features.project.data.export

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.time.Instant

class ProjectExportMapperTest {

    private val start = Instant.parse("2026-09-01T08:00:00Z")
    private val later = Instant.parse("2026-09-02T08:00:00Z")
    private val exportedAt = Instant.parse("2026-09-25T12:00:00Z")

    // A running interval: no end yet.
    private fun interval(id: String, startUtc: Instant) =
        TaskInterval(id, "t", "p", startUtc, null, durationMillis = 61_000)

    private fun subTask(id: String, sortIndex: Long?) =
        ProjectSubTask(
            projectSubTaskId = id,
            parentProjectTaskId = "t",
            parentProjectId = "p",
            title = id,
            durationMillis = 1_000,
            isTimerRunning = false,
            startDateTimeUtc = start,
            sortIndex = sortIndex,
            subTaskIntervals = listOf(SubTaskInterval("si-$id", "i", id, "p", start, later, durationMillis = 1_000)),
        )

    private fun task(id: String, sortIndex: Long?) =
        ProjectTask(
            projectTaskId = id,
            title = id,
            description = null,
            durationMillis = null,
            startDateTimeUtc = start,
            parentProjectId = "p",
            isTimerRunning = false,
            sortIndex = sortIndex,
        )

    private fun project(tasks: List<ProjectTask>? = emptyList()) =
        Project(
            projectId = "p1",
            title = "Client work",
            description = "notes",
            colorArgb = 0xFF1A2B3C.toInt(),
            totalDurationMillis = 90_061_000,
            startDateTimeUtc = start,
            isFinished = false,
            endDateTimeUtc = later,
            projectTasks = tasks,
        )

    private fun Project.export() = toProjectExportDto(exportedAt, TimeZone.of("Europe/Berlin"))

    private fun exported(project: Project) = project.export().project

    @Test
    fun mapsEnvelopeAndProjectFields() {
        val dto = project().export()

        assertThat(listOf(dto.formatVersion, dto.app, dto.exportedAt, dto.timeZone))
            .containsExactly(1, "Tracky", "2026-09-25T12:00:00Z", "Europe/Berlin")
        with(dto.project) {
            assertThat(
                listOf(id, title, description, color, status),
            ).containsExactly("p1", "Client work", "notes", "#1A2B3C", "active")
            assertThat(listOf(start, end)).containsExactly("2026-09-01T08:00:00Z", "2026-09-02T08:00:00Z")
            assertThat(listOf(totalDurationMs, totalDuration)).containsExactly(90_061_000L, "25:01:01")
        }
    }

    @Test
    fun mapsColorStatusAndUnloadedTasks() {
        assertThat(exported(project().copy(colorArgb = 0x00000A0B)).color).isEqualTo("#000A0B")
        assertThat(exported(project().copy(colorArgb = null)).color).isNull()
        assertThat(exported(project().copy(isFinished = true)).status).isEqualTo("finished")
        assertThat(exported(project().copy(isArchived = true)).status).isEqualTo("archived")
        assertThat(exported(project().copy(isArchived = true, trashedAt = later)).status).isEqualTo("trashed")
        assertThat(exported(project(tasks = null)).tasks).isEmpty()
    }

    @Test
    fun ordersTasksSubtasksAndIntervals() {
        val first =
            task("first", 0).copy(
                intervals = listOf(interval("late", later), interval("early", start)),
                subTasks = listOf(subTask("b", null), subTask("a", 1)),
            )

        val tasks = exported(project(tasks = listOf(task("never-dragged", null), first))).tasks

        assertThat(tasks.map { it.id }).containsExactly("first", "never-dragged")
        assertThat(tasks[0].intervals.map { it.id }).containsExactly("early", "late")
        assertThat(tasks[0].subtasks.map { it.id }).containsExactly("a", "b")
        assertThat(tasks[0].subtasks[0].intervals.map { it.end }).containsExactly("2026-09-02T08:00:00Z")
    }

    @Test
    fun mapsDurationsAndKeepsARunningIntervalOpen() {
        val running = task("t", 0).copy(intervals = listOf(interval("i", start)))
        val task = exported(project(listOf(running))).tasks.single()

        assertThat(listOf(task.durationMs, task.duration, task.end)).containsExactly(0L, "00:00:00", null)
        with(task.intervals.single()) {
            assertThat(listOf(end, durationMs, duration)).containsExactly(null, 61_000L, "00:01:01")
        }
    }
}
