package com.jvcs.tracky.features.project.data.subtask

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeServerClockOffsetStore
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.projecttracker.data.AlreadyReconciled
import com.jvcs.tracky.features.projecttracker.data.FakeActiveTimerRepository
import com.jvcs.tracky.features.projecttracker.data.FakeDb
import com.jvcs.tracky.features.projecttracker.data.FakeLocalSubTaskDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeLocalTaskDataSource
import com.jvcs.tracky.features.projecttracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeRemoteSubTaskDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
    private val activeTimer = FakeActiveTimerRepository()

    private val repository =
        OfflineFirstSubTaskRepository(
            startupReconciliation = AlreadyReconciled,
            localSubTaskDataSource = localSubTasks,
            remoteSubTaskDataSource = remoteSubTasks,
            localTaskDataSource = localTasks,
            intervalRepository = intervals,
            subTaskIntervalRepository = subTaskIntervals,
            projectTaskRepository = tasks,
            activeTimerRepository = activeTimer,
            deviceIdProvider = FakeDeviceIdProvider(),
            serverClock = ServerClock(timeProvider, FakeServerClockOffsetStore()),
            pendingSyncDataSource = queue,
            syncScheduler = FakeSyncScheduler(),
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
            timeProvider = timeProvider,
        )

    private fun subTaskInterval(startedParent: Boolean) =
        SubTaskInterval(
            subTaskIntervalId = "si1",
            parentSubTaskId = "s1",
            parentTaskIntervalId = "i1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = null,
            durationMillis = 0,
            startedParentTimer = startedParent,
        )

    private fun taskInterval() =
        TaskInterval(
            intervalId = "i1",
            parentTaskId = "t1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = null,
            durationMillis = 0,
        )

    /** Seeds the whole chain so the subtask row is readable after the timer write. */
    private fun seedTree() {
        db.seedProject("p1")
        db.seedTask("t1", "p1")
        db.seedSubTask("s1", "t1", "p1")
    }

    @Test
    fun startingASubTaskAnnouncesOneTimerAndPushesTheTwoRows() =
        runTest {
            seedTree()
            localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

            repository.startSubTask("s1")

            // One call, not two pushes: the timer resource opens the enclosing task interval itself
            // from the id named here, at the same instant. Pushing both rows as well would race it.
            val (taskInterval, subTaskInterval) = activeTimer.starts.single()
            assertThat(taskInterval.intervalId).isEqualTo("i1")
            assertThat(subTaskInterval?.subTaskIntervalId).isEqualTo("si1")
            assertThat(intervals.created.isEmpty()).isTrue()
            assertThat(subTaskIntervals.created.isEmpty()).isTrue()
            // Both timer flags flipped, so both rows still have to go the ordinary way.
            assertThat(tasks.upserted).isEqualTo(listOf("t1"))
            assertThat(remoteSubTasks.updatedSubTaskIds).isEqualTo(listOf("s1"))
        }

    @Test
    fun startingASubTaskInsideARunningTaskStillPushesItsOwnInterval() =
        runTest {
            seedTree()
            // No task interval in the change: that timer was already running, so its row and interval
            // are unchanged. The subtask's own two rows are not.
            localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

            repository.startSubTask("s1")

            assertThat(intervals.created.isEmpty()).isTrue()
            assertThat(tasks.upserted.isEmpty()).isTrue()
            assertThat(subTaskIntervals.created).isEqualTo(listOf("si1"))
            assertThat(remoteSubTasks.updatedSubTaskIds).isEqualTo(listOf("s1"))
        }

    @Test
    fun stoppingASubTaskThatClosesTheTaskIntervalPushesBothIntervalsAsUpdates() =
        runTest {
            seedTree()
            localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

            repository.stopSubTask("s1")

            // Update, not create — the server already knows both rows from the start push.
            assertThat(intervals.updated).isEqualTo(listOf("i1"))
            assertThat(subTaskIntervals.updated).isEqualTo(listOf("si1"))
            assertThat(intervals.created.isEmpty() && subTaskIntervals.created.isEmpty()).isTrue()
            assertThat(tasks.upserted).isEqualTo(listOf("t1"))
        }

    @Test
    fun stoppingASubTaskThatLeavesTheTaskRunningStillPushesItsOwnInterval() =
        runTest {
            seedTree()
            localSubTasks.stopResult = SubTaskTimerChange(subTaskInterval(false), taskInterval = null)

            repository.stopSubTask("s1")

            assertThat(intervals.updated.isEmpty()).isTrue()
            assertThat(tasks.upserted.isEmpty()).isTrue()
            assertThat(subTaskIntervals.updated).isEqualTo(listOf("si1"))
        }

    @Test
    fun stoppingASubTaskThatWasNotRunningIsANoOp() =
        runTest {
            localSubTasks.stopResult = null

            val result = repository.stopSubTask("s1")

            assertThat(result is Result.Success).isTrue()
            assertThat(intervals.updated.isEmpty()).isTrue()
            assertThat(subTaskIntervals.updated.isEmpty()).isTrue()
            assertThat(tasks.upserted.isEmpty()).isTrue()
        }

    @Test
    fun theRemainingRowsAreStillPushedWhenTheTimerCallFails() =
        runTest {
            seedTree()
            // Each push has its own offline queue, so stopping early would silently drop writes.
            activeTimer.result = Result.Error(DataError.Remote.NO_INTERNET)
            localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

            repository.startSubTask("s1")

            assertThat(tasks.upserted).isEqualTo(listOf("t1"))
            assertThat(remoteSubTasks.updatedSubTaskIds).isEqualTo(listOf("s1"))
        }

    @Test
    fun aFailedTimerCallWinsOverASucceedingRowPush() =
        runTest {
            seedTree()
            activeTimer.result = Result.Error(DataError.Remote.SERVER_ERROR)
            localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

            val result = repository.startSubTask("s1")

            // A lost interval is a lost measurement; a task row is recomputable from its intervals.
            assertThat(result).isEqualTo(Result.Error(DataError.Remote.SERVER_ERROR))
        }

    @Test
    fun aSubTaskStartFallsBackToTheOldPushes_whenItsTaskIsStillQueuedForCreation() =
        runTest {
            // A task that exists only here has nothing for the timer resource to hang off, so the two
            // intervals go the ordinary queued way instead of spending a doomed request.
            seedTree()
            queue.enqueue(
                entityId = "t1",
                entityType = PendingSyncOperation.ENTITY_TASK,
                operationType = PendingSyncOperation.OP_CREATE,
                parentEntityId = "p1",
                createdAt = Instant.fromEpochMilliseconds(0),
            )
            localSubTasks.startResult = SubTaskTimerChange(subTaskInterval(true), taskInterval())

            repository.startSubTask("s1")

            assertThat(activeTimer.starts.isEmpty()).isTrue()
            assertThat(intervals.created).isEqualTo(listOf("i1"))
            assertThat(subTaskIntervals.created).isEqualTo(listOf("si1"))
        }

    @Test
    fun stoppingAForeignSubTaskTimerNeverBanksItLocally() =
        runTest {
            // The same double count the task path guards against, one level down: stopSubTask is what
            // banks, and the server's rows already carry a foreign timer's time.
            seedTree()
            subTaskIntervals.openInterval =
                subTaskInterval(false)
                    .copy(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)

            repository.stopSubTask("s1")

            assertThat(localSubTasks.stopped.isEmpty()).isTrue()
            val (intervalId, kind, _) = activeTimer.stops.single()
            assertThat(intervalId).isEqualTo("si1")
            assertThat(kind).isEqualTo(ActiveTimerKind.SUB_TASK)
        }

    @Test
    fun stoppingOwnSubTaskTimerBanksItAndTellsTheServer() =
        runTest {
            // Null device id means "this device", so the ordinary local close and bank still happens.
            seedTree()
            subTaskIntervals.openInterval = subTaskInterval(false).copy(startedByDeviceId = null)
            localSubTasks.stopResult =
                SubTaskTimerChange(
                    subTaskInterval(false).copy(endDateTimeUtc = Instant.fromEpochMilliseconds(60_000)),
                    taskInterval = null,
                )

            repository.stopSubTask("s1")

            assertThat(localSubTasks.stopped).isEqualTo(listOf("s1"))
            assertThat(activeTimer.stops.single().third).isEqualTo(Instant.fromEpochMilliseconds(60_000))
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

    /** What the foreign-timer guard reads before deciding whether to bank locally. */
    var openInterval: SubTaskInterval? = null

    override suspend fun getOpenIntervalBySubTaskId(subTaskId: String) = Result.Success(openInterval)

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

    override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long) = Result.Success(Unit)

    override suspend fun updateProjectTaskTitle(taskId: String, title: String) = Result.Success(Unit)

    override suspend fun updateProjectTaskText(
        taskId: String,
        title: String,
        description: String?,
    ) = Result.Success(Unit)

    override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)

    override suspend fun startProjectTask(taskId: String) = Result.Success(Unit)

    override suspend fun stopProjectTask(taskId: String) = Result.Success(Unit)

    override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>) = Result.Success(Unit)

    override suspend fun syncPendingTasks() = Unit
}
