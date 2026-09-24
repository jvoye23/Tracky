package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The ordering contract for tasks and subtasks. It is the opposite of the project one on purpose:
 * projects sort nulls first so a new project lands on top of the overview, while tasks are numbered
 * `01, 02, 03…` top-down, so a new one belongs at the bottom.
 */
class TaskSortOrderTest {

    private fun task(
        id: String,
        sortIndex: Long?,
        createdAtMs: Long,
    ) = ProjectTask(
        projectTaskId = id,
        title = id,
        description = null,
        durationMillis = 0L,
        startDateTimeUtc = Instant.fromEpochMilliseconds(createdAtMs),
        parentProjectId = "p1",
        isTimerRunning = false,
        sortIndex = sortIndex,
    )

    private fun subTask(
        id: String,
        sortIndex: Long?,
        createdAtMs: Long,
    ) = ProjectSubTask(
        projectSubTaskId = id,
        parentProjectTaskId = "t1",
        parentProjectId = "p1",
        title = id,
        durationMillis = 0L,
        isTimerRunning = false,
        startDateTimeUtc = Instant.fromEpochMilliseconds(createdAtMs),
        sortIndex = sortIndex,
    )

    @Test
    fun indexedTasksSortByTheirIndex() {
        val sorted =
            listOf(
                task("c", sortIndex = 2, createdAtMs = 0),
                task("a", sortIndex = 0, createdAtMs = 0),
                task("b", sortIndex = 1, createdAtMs = 0),
            ).sortedByTaskOrder()

        assertEquals(listOf("a", "b", "c"), sorted.map { it.projectTaskId })
    }

    @Test
    fun aTaskThatHasNeverBeenDraggedSortsAfterEveryIndexedOne() {
        // The whole reason the migration needs no backfill: a fresh task keeps sortIndex = null and
        // still lands where the user expects it, at the end of the list.
        val sorted =
            listOf(
                task("new", sortIndex = null, createdAtMs = 500),
                task("dragged", sortIndex = 9, createdAtMs = 100),
            ).sortedByTaskOrder()

        assertEquals(listOf("dragged", "new"), sorted.map { it.projectTaskId })
    }

    @Test
    fun unindexedTasksFallBackToCreationOrderOldestFirst() {
        // This is what gives a never-reordered project a deterministic order at all: the Room
        // @Relation behind it carries no ORDER BY.
        val sorted =
            listOf(
                task("third", sortIndex = null, createdAtMs = 300),
                task("first", sortIndex = null, createdAtMs = 100),
                task("second", sortIndex = null, createdAtMs = 200),
            ).sortedByTaskOrder()

        assertEquals(listOf("first", "second", "third"), sorted.map { it.projectTaskId })
    }

    @Test
    fun creationOrderBreaksTiesBetweenEqualIndices() {
        // Duplicate indices are legal between batches, so the comparator has to stay total.
        val sorted =
            listOf(
                task("later", sortIndex = 1, createdAtMs = 200),
                task("earlier", sortIndex = 1, createdAtMs = 100),
            ).sortedByTaskOrder()

        assertEquals(listOf("earlier", "later"), sorted.map { it.projectTaskId })
    }

    @Test
    fun subTasksFollowTheSameRule() {
        val sorted =
            listOf(
                subTask("new", sortIndex = null, createdAtMs = 400),
                subTask("second", sortIndex = 1, createdAtMs = 100),
                subTask("first", sortIndex = 0, createdAtMs = 300),
            ).sortedBySubTaskOrder()

        assertEquals(listOf("first", "second", "new"), sorted.map { it.projectSubTaskId })
    }

    @Test
    fun sortingIsStableForAnAlreadyOrderedList() {
        val ordered =
            listOf(
                task("a", sortIndex = 0, createdAtMs = 0),
                task("b", sortIndex = 1, createdAtMs = 0),
                task("c", sortIndex = 2, createdAtMs = 0),
            )

        assertEquals(ordered, ordered.sortedByTaskOrder())
    }
}
