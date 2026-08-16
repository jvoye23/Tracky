@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The [Timestamped] roll-up: [Timestamped.lastUpdatedAt] is the max of an element's own stamp and
 * every stamp in its subtree, while [Timestamped.ownUpdatedAt] stays the element's own value —
 * that separation is what last-write-wins sync depends on.
 */
class TimestampedTest {

    private val t100 = Instant.fromEpochMilliseconds(100)
    private val t200 = Instant.fromEpochMilliseconds(200)
    private val t300 = Instant.fromEpochMilliseconds(300)

    @Test
    fun projectWithoutTasksRollsUpToItsOwnStamp() {
        val project = project(ownUpdatedAt = t200, projectTasks = emptyList())

        assertEquals(t200, project.lastUpdatedAt)
    }

    @Test
    fun unloadedTasksRollUpToTheProjectsOwnStamp() {
        // projectTasks == null means "not loaded" (ProjectEntity.toProject), not "no tasks".
        val project = project(ownUpdatedAt = t200, projectTasks = null)

        assertEquals(t200, project.lastUpdatedAt)
    }

    @Test
    fun newerTaskWinsOverTheProjectsOwnStamp() {
        val project = project(
            ownUpdatedAt = t100,
            projectTasks = listOf(task("t1", ownUpdatedAt = t300))
        )

        assertEquals(t300, project.lastUpdatedAt)
        assertEquals(t100, project.ownUpdatedAt) // own stamp is untouched by the roll-up
    }

    @Test
    fun newerProjectWinsOverItsTasks() {
        val project = project(
            ownUpdatedAt = t300,
            projectTasks = listOf(task("t1", ownUpdatedAt = t100), task("t2", ownUpdatedAt = t200))
        )

        assertEquals(t300, project.lastUpdatedAt)
    }

    @Test
    fun theNewestTaskWinsAmongSiblings() {
        val project = project(
            ownUpdatedAt = null,
            projectTasks = listOf(
                task("t1", ownUpdatedAt = t100),
                task("t2", ownUpdatedAt = t300),
                task("t3", ownUpdatedAt = t200)
            )
        )

        assertEquals(t300, project.lastUpdatedAt)
    }

    @Test
    fun aTaskWithoutAStampDoesNotHideItsSiblings() {
        val project = project(
            ownUpdatedAt = null,
            projectTasks = listOf(task("t1", ownUpdatedAt = null), task("t2", ownUpdatedAt = t200))
        )

        assertEquals(t200, project.lastUpdatedAt)
    }

    @Test
    fun noStampsAnywhereRollUpToNull() {
        val project = project(
            ownUpdatedAt = null,
            projectTasks = listOf(task("t1", ownUpdatedAt = null))
        )

        assertNull(project.lastUpdatedAt)
    }

    @Test
    fun intervalsNeverContributeToTheRollUp() {
        // TaskInterval has no stamp of its own, so a task's roll-up is just the task's own stamp.
        val task = task("t1", ownUpdatedAt = t100, intervals = listOf(interval("i1"), interval("i2")))

        assertNull(interval("i1").lastUpdatedAt)
        assertEquals(t100, task.lastUpdatedAt)
        assertEquals(t100, project(ownUpdatedAt = null, projectTasks = listOf(task)).lastUpdatedAt)
    }

    // ---------------------------------------------------------------------------------------------

    private fun project(ownUpdatedAt: Instant?, projectTasks: List<ProjectTask>?) = Project(
        projectId = "p1",
        title = "title",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null,
        projectTasks = projectTasks,
        ownUpdatedAt = ownUpdatedAt
    )

    private fun task(
        id: String,
        ownUpdatedAt: Instant?,
        intervals: List<TaskInterval> = emptyList()
    ) = ProjectTask(
        projectTaskId = id,
        title = "title-$id",
        durationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = "p1",
        isTimerRunning = false,
        intervals = intervals,
        ownUpdatedAt = ownUpdatedAt
    )

    private fun interval(id: String) = TaskInterval(
        intervalId = id,
        parentTaskId = "t1",
        parentProjectId = "p1",
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = null,
        durationMillis = 0L
    )
}
