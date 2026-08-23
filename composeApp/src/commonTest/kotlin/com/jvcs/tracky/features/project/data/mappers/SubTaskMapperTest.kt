@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project.data.mappers

import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.relation.SubTaskWithIntervals
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Round-trips the subtask mappers. Every field crosses an epoch-millis/[Instant] boundary or a
 * name change, so a silently dropped or swapped one is exactly the kind of bug that survives
 * compilation — the round-trip is what pins it down.
 */
class SubTaskMapperTest {

    private val t100 = Instant.fromEpochMilliseconds(100)
    private val t300 = Instant.fromEpochMilliseconds(300)

    private fun subTask(
        durationMillis: Long? = 5_000,
        description: String? = "notes",
        endDateTimeUtc: Instant? = t300,
        ownUpdatedAt: Instant? = t300,
        intervals: List<SubTaskInterval> = emptyList()
    ) = ProjectSubTask(
        projectSubTaskId = "s1",
        parentProjectTaskId = "t1",
        parentProjectId = "p1",
        title = "title",
        description = description,
        durationMillis = durationMillis,
        isTimerRunning = true,
        startDateTimeUtc = t100,
        endDateTimeUtc = endDateTimeUtc,
        isFinished = true,
        subTaskIntervals = intervals,
        ownUpdatedAt = ownUpdatedAt,
    )

    private fun interval(id: String = "si1", endDateTimeUtc: Instant? = t300) = SubTaskInterval(
        subTaskIntervalId = id,
        parentSubTaskId = "s1",
        parentTaskIntervalId = "i1",
        parentProjectId = "p1",
        startDateTimeUtc = t100,
        endDateTimeUtc = endDateTimeUtc,
        durationMillis = 200L
    )

    @Test
    fun subTaskSurvivesARoundTrip() {
        val original = subTask()

        assertEquals(original, original.toProjectSubTaskEntity().toProjectSubTask())
    }

    @Test
    fun subTaskKeepsItsNullsApart() {
        // A null duration is "never timed" and a null stamp is "never edited" — neither may be
        // flattened to a zero on the way through the entity, which is what updatedAtEpochMs = 0
        // used to mean before migration 10->11.
        val original = subTask(durationMillis = null, description = null, endDateTimeUtc = null, ownUpdatedAt = null)

        val roundTripped = original.toProjectSubTaskEntity().toProjectSubTask()

        assertEquals(original, roundTripped)
        assertNull(roundTripped.durationMillis)
        assertNull(roundTripped.description)
        assertNull(roundTripped.endDateTimeUtc)
        assertNull(roundTripped.ownUpdatedAt)
    }

    @Test
    fun subTaskIntervalSurvivesARoundTrip() {
        val original = interval()

        assertEquals(original, original.toSubTaskIntervalEntity().toSubTaskInterval())
    }

    @Test
    fun anOpenSubTaskIntervalStaysOpen() {
        // A null end time is what marks the timer as running on this device; losing it would make
        // a pull think the interval was closed.
        val roundTripped = interval(endDateTimeUtc = null).toSubTaskIntervalEntity().toSubTaskInterval()

        assertNull(roundTripped.endDateTimeUtc)
    }

    @Test
    fun subTaskCarriesItsIntervalsWhenReadAsARelation() {
        val relation = SubTaskWithIntervals(
            subTask = subTask().toProjectSubTaskEntity(),
            intervals = listOf(interval("si1"), interval("si2")).map { it.toSubTaskIntervalEntity() }
        )

        val mapped = relation.toProjectSubTask()

        assertEquals(subTask(intervals = listOf(interval("si1"), interval("si2"))), mapped)
    }

    /** A subtask interval carries no stamp of its own, so the task rolls up to the subtask's. */
    @Test
    fun taskRelationCarriesBothItsIntervalsAndItsSubTasks() {
        val relation = TaskWithSubTasks(
            task = ProjectTaskEntity(
                recordId = "t1",
                parentProjectId = "p1",
                description = "task title",
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = 100,
            ),
            intervals = listOf(
                TaskIntervalEntity("i1", "t1", "p1", 0, 60_000, 60_000)
            ),
            subTasks = listOf(
                SubTaskWithIntervals(
                    subTask = subTask().toProjectSubTaskEntity(),
                    intervals = listOf(interval().toSubTaskIntervalEntity())
                )
            )
        )

        val mapped = relation.toProjectTask()

        assertEquals("task title", mapped.title) // the entity column is named `description`
        assertEquals(listOf("i1"), mapped.intervals.map { it.intervalId })
        assertEquals(listOf("s1"), mapped.subTasks?.map { it.projectSubTaskId })
        assertEquals(listOf("si1"), mapped.subTasks?.single()?.subTaskIntervals?.map { it.subTaskIntervalId })
        // Both branches stay visible to the Timestamped roll-up.
        assertEquals(2, mapped.children.size)
        assertEquals(t300, mapped.lastUpdatedAt)
    }
}
