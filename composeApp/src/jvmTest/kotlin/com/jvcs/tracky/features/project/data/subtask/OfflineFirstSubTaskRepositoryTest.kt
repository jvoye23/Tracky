package com.jvcs.tracky.features.project.data.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project_tracker.data.FakeDb
import com.jvcs.tracky.features.project_tracker.data.FakeLocalSubTaskDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeLocalTaskDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a subtask write pushes, and what it deliberately does not.
 *
 * Subtasks are local-only, so the interesting cases are all about the *task* interval a subtask
 * timer opens or closes: those rows sync, and if the repository failed to hand them on, time tracked
 * through subtasks would silently never reach the server.
 */
internal class OfflineFirstSubTaskRepositoryTest {

    private val db = FakeDb()
    private val localTasks = FakeLocalTaskDataSource(db)
    private val localSubTasks = FakeLocalSubTaskDataSource(db)
    private val intervals = RecordingIntervalRepository()
    private val tasks = RecordingTaskRepository()
    private val timeProvider = FakeTimeProvider(now = Instant.fromEpochMilliseconds(500))

    private val repository = OfflineFirstSubTaskRepository(
        localSubTaskDataSource = localSubTasks,
        localTaskDataSource = localTasks,
        intervalRepository = intervals,
        projectTaskRepository = tasks,
        timeProvider = timeProvider
    )

    private fun subTaskInterval(startedParent: Boolean) = SubTaskInterval(
        subTaskIntervalId = "si1", parentSubTaskId = "s1", parentTaskIntervalId = "i1",
        parentProjectId = "p1", startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = null, durationMillis = 0, startedParentTimer = startedParent
    )

    private fun taskInterval() = TaskInterval(
        intervalId = "i1", parentTaskId = "t1", parentProjectId = "p1",
        startDateTimeUtc = Instant.fromEpochMilliseconds(0), endDateTimeUtc = null, durationMillis = 0
    )

    @Test
    fun startingASubTaskThatOpensATaskIntervalPushesItAndTheTask() = runTest {
        db.seedProject("p1"); db.seedTask("t1", "p1")
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        repository.startSubTask("s1")

        assertEquals(listOf("i1"), intervals.created)
        assertTrue(intervals.updated.isEmpty())
        // The task's isTimerRunning flipped, so its row has to go too.
        assertEquals(listOf("t1"), tasks.upserted)
    }

    @Test
    fun startingASubTaskInsideARunningTaskPushesNothing() = runTest {
        db.seedProject("p1"); db.seedTask("t1", "p1")
        // No task interval in the change: the timer was already running, so nothing new was created.
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

        repository.startSubTask("s1")

        assertTrue(intervals.created.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
    }

    @Test
    fun stoppingASubTaskThatClosesTheTaskIntervalPushesItAsAnUpdate() = runTest {
        db.seedProject("p1"); db.seedTask("t1", "p1")
        localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        repository.stopSubTask("s1")

        // Update, not create — the server already knows this row from the start push.
        assertEquals(listOf("i1"), intervals.updated)
        assertTrue(intervals.created.isEmpty())
        assertEquals(listOf("t1"), tasks.upserted)
    }

    @Test
    fun stoppingASubTaskThatLeavesTheTaskRunningPushesNothing() = runTest {
        db.seedProject("p1"); db.seedTask("t1", "p1")
        localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

        repository.stopSubTask("s1")

        assertTrue(intervals.updated.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
    }

    @Test
    fun stoppingASubTaskThatWasNotRunningIsANoOp() = runTest {
        localSubTasks.stopResult = null

        val result = repository.stopSubTask("s1")

        assertTrue(result is Result.Success)
        assertTrue(intervals.updated.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
    }

    @Test
    fun subTaskWritesStayLocalAndCarryAFreshStamp() = runTest {
        val subTask = ProjectSubTask(
            projectSubTaskId = "s1", parentProjectTaskId = "t1", parentProjectId = "p1",
            title = "sub", durationMillis = null, isTimerRunning = false,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0)
        )

        repository.upsertSubTask(subTask)
        repository.deleteSubTask("s1")

        // Nothing queued, nothing pushed — there is no subtask route to call.
        assertTrue(intervals.created.isEmpty() && tasks.upserted.isEmpty())
        assertEquals(timeProvider.now, localSubTasks.upserted.single().ownUpdatedAt)
        assertEquals(listOf("s1"), localSubTasks.deleted)
    }
}

private class RecordingIntervalRepository : IntervalRepository {
    val created = mutableListOf<String>()
    val updated = mutableListOf<String>()

    override suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
        created += interval.intervalId
        return Result.Success(Unit)
    }
    override suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
        updated += interval.intervalId
        return Result.Success(Unit)
    }
    override suspend fun deleteTaskInterval(intervalId: String) = Result.Success(Unit)
    override suspend fun getOpenIntervalByTaskId(taskId: String) = Result.Success(null)
    override suspend fun syncPendingIntervals() = Unit
}

private class RecordingTaskRepository : ProjectTaskRepository {
    val upserted = mutableListOf<String>()

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
        upserted += projectTask.projectTaskId
        return Result.Success(Unit)
    }
    override suspend fun deleteProjectTask(projectId: String, taskId: String) = Result.Success(Unit)
    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long) =
        Result.Success(Unit)
    override suspend fun updateProjectTaskTitle(taskId: String, title: String) = Result.Success(Unit)
    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)
    override suspend fun startProjectTask(taskId: String) = Result.Success(Unit)
    override suspend fun stopProjectTask(taskId: String) = Result.Success(Unit)
    override suspend fun syncPendingTasks() = Unit
}
