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
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project_tracker.data.FakeDb
import com.jvcs.tracky.features.project_tracker.data.FakeLocalSubTaskDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeLocalTaskDataSource
import com.jvcs.tracky.features.project_tracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeRemoteSubTaskDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a subtask timer pushes, in what order, and which error wins.
 *
 * A subtask timer touches four rows that sync: the task interval it may open or close, its own
 * subtask interval, and the task and subtask rows whose duration and timer flag it changed. Three
 * of the four belong to other repositories, and if this one failed to hand them on, time tracked
 * through subtasks would silently never reach the server. The subtask's own CRUD push is
 * OfflineFirstSubTaskPushTest's subject.
 */
internal class OfflineFirstSubTaskRepositoryTest {

    private val db = FakeDb()
    private val localTasks = FakeLocalTaskDataSource(db)
    private val localSubTasks = FakeLocalSubTaskDataSource(db)
    private val intervals = RecordingIntervalRepository()
    private val subTaskIntervals = RecordingSubTaskIntervalRepository()
    private val tasks = RecordingTaskRepository()
    private val timeProvider = FakeTimeProvider(now = Instant.fromEpochMilliseconds(500))

    private val queue = FakePendingSyncDataSource()
    private val remoteSubTasks = FakeRemoteSubTaskDataSource()

    private val repository = OfflineFirstSubTaskRepository(
        localSubTaskDataSource = localSubTasks,
        remoteSubTaskDataSource = remoteSubTasks,
        localTaskDataSource = localTasks,
        intervalRepository = intervals,
        subTaskIntervalRepository = subTaskIntervals,
        projectTaskRepository = tasks,
        pendingSyncDataSource = queue,
        syncScheduler = FakeSyncScheduler(),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
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

    /** Seeds the whole chain so the subtask row is readable after the timer write. */
    private fun seedTree() {
        db.seedProject("p1"); db.seedTask("t1", "p1"); db.seedSubTask("s1", "t1", "p1")
    }

    @Test
    fun startingASubTaskThatOpensATaskIntervalPushesAllFourRows() = runTest {
        seedTree()
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        repository.startSubTask("s1")

        assertEquals(listOf("i1"), intervals.created)
        assertEquals(listOf("si1"), subTaskIntervals.created)
        // Both timer flags flipped, so both rows have to go too.
        assertEquals(listOf("t1"), tasks.upserted)
        assertEquals(listOf("s1"), remoteSubTasks.updatedSubTaskIds)
    }

    @Test
    fun startingASubTaskInsideARunningTaskStillPushesItsOwnInterval() = runTest {
        seedTree()
        // No task interval in the change: that timer was already running, so its row and interval
        // are unchanged. The subtask's own two rows are not.
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

        repository.startSubTask("s1")

        assertTrue(intervals.created.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
        assertEquals(listOf("si1"), subTaskIntervals.created)
        assertEquals(listOf("s1"), remoteSubTasks.updatedSubTaskIds)
    }

    @Test
    fun stoppingASubTaskThatClosesTheTaskIntervalPushesBothIntervalsAsUpdates() = runTest {
        seedTree()
        localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        repository.stopSubTask("s1")

        // Update, not create — the server already knows both rows from the start push.
        assertEquals(listOf("i1"), intervals.updated)
        assertEquals(listOf("si1"), subTaskIntervals.updated)
        assertTrue(intervals.created.isEmpty() && subTaskIntervals.created.isEmpty())
        assertEquals(listOf("t1"), tasks.upserted)
    }

    @Test
    fun stoppingASubTaskThatLeavesTheTaskRunningStillPushesItsOwnInterval() = runTest {
        seedTree()
        localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

        repository.stopSubTask("s1")

        assertTrue(intervals.updated.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
        assertEquals(listOf("si1"), subTaskIntervals.updated)
    }

    @Test
    fun stoppingASubTaskThatWasNotRunningIsANoOp() = runTest {
        localSubTasks.stopResult = null

        val result = repository.stopSubTask("s1")

        assertTrue(result is Result.Success)
        assertTrue(intervals.updated.isEmpty())
        assertTrue(subTaskIntervals.updated.isEmpty())
        assertTrue(tasks.upserted.isEmpty())
    }

    @Test
    fun everyRowIsStillPushedWhenAnEarlierOneFails() = runTest {
        seedTree()
        // Each push has its own offline queue, so stopping early would silently drop writes.
        intervals.failWith = DataError.Remote.NO_INTERNET
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        repository.startSubTask("s1")

        assertEquals(listOf("si1"), subTaskIntervals.created)
        assertEquals(listOf("t1"), tasks.upserted)
    }

    @Test
    fun aFailedTaskIntervalPushWinsOverAFailedSubTaskIntervalPush() = runTest {
        seedTree()
        intervals.failWith = DataError.Remote.SERVER_ERROR
        subTaskIntervals.failWith = DataError.Remote.NO_INTERNET
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        val result = repository.startSubTask("s1")

        // First error in push order wins: outer interval before inner.
        assertEquals(Result.Error(DataError.Remote.SERVER_ERROR), result)
    }

    @Test
    fun aFailedSubTaskIntervalPushWinsOverASucceedingTaskRowPush() = runTest {
        seedTree()
        subTaskIntervals.failWith = DataError.Remote.NO_INTERNET
        localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

        val result = repository.startSubTask("s1")

        // A lost interval is a lost measurement; a task row is recomputable from its intervals.
        assertEquals(Result.Error(DataError.Remote.NO_INTERNET), result)
    }
}

private class RecordingSubTaskIntervalRepository : SubTaskIntervalRepository {
    val created = mutableListOf<String>()
    val updated = mutableListOf<String>()
    var failWith: DataError? = null

    override suspend fun createSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> {
        created += interval.subTaskIntervalId
        return failWith?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
    override suspend fun updateSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> {
        updated += interval.subTaskIntervalId
        return failWith?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
    override suspend fun deleteSubTaskInterval(intervalId: String) = Result.Success(Unit)
    override suspend fun getOpenIntervalBySubTaskId(subTaskId: String) = Result.Success(null)
    override suspend fun syncPendingSubTaskIntervals() = Unit
}

private class RecordingIntervalRepository : IntervalRepository {
    val created = mutableListOf<String>()
    val updated = mutableListOf<String>()
    var failWith: DataError? = null

    override suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
        created += interval.intervalId
        return failWith?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
    override suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
        updated += interval.intervalId
        return failWith?.let { Result.Error(it) } ?: Result.Success(Unit)
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
