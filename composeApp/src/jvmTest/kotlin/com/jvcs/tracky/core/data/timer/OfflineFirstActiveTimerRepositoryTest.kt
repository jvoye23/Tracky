package com.jvcs.tracky.core.data.timer

import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.sync.DeltaSyncApplier
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.RemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.timer.ActiveTimer
import com.jvcs.tracky.core.domain.timer.ActiveTimerChange
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.RemoteActiveTimerDataSource
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeServerClockOffsetStore
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project_tracker.data.FakeLocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeRemoteProjectDataSource
import com.jvcs.tracky.features.project_tracker.data.FakeSyncScheduler
import com.jvcs.tracky.core.domain.sync.FakeSyncCursorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A scripted active-timer endpoint: one queued answer per call, and a record of what was sent. */
private class FakeRemoteActiveTimerDataSource : RemoteActiveTimerDataSource {
    val starts = mutableListOf<StartActiveTimer>()
    val stops = mutableListOf<Pair<String, Instant>>()
    var nextStart: Result<ActiveTimerChange, DataError.Remote>? = null
    var nextStop: Result<ActiveTimerChange, DataError.Remote>? = null

    override suspend fun getActive(): Result<ActiveTimer?, DataError.Remote> = Result.Success(null)

    override suspend fun start(request: StartActiveTimer): Result<ActiveTimerChange, DataError.Remote> {
        starts += request
        return nextStart ?: Result.Success(applied())
    }

    override suspend fun stop(
        intervalId: String,
        endedAt: Instant
    ): Result<ActiveTimerChange, DataError.Remote> {
        stops += intervalId to endedAt
        return nextStop ?: Result.Success(applied())
    }
}

private fun applied(
    taskIntervals: List<TaskInterval> = emptyList(),
    serverNow: Instant? = null
) = ActiveTimerChange.Applied(null, taskIntervals, emptyList(), serverNow)

/** Counts pulls, so "a refusal re-syncs" can be asserted without reaching into DeltaSyncApplier. */
private class CountingRemoteSyncDataSource : RemoteSyncDataSource {
    var pulls = 0
    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> {
        pulls++
        return Result.Success(
            SyncChanges(
                cursor = since ?: 0,
                serverNow = null,
                fullResyncRequired = false,
                hasMore = false,
                projects = emptyList(),
                tasks = emptyList(),
                taskIntervals = emptyList(),
                subTasks = emptyList(),
                subTaskIntervals = emptyList(),
                tombstones = emptyList()
            )
        )
    }
}

class OfflineFirstActiveTimerRepositoryTest {

    private val remote = FakeRemoteActiveTimerDataSource()
    private val local = FakeLocalProjectDataSource()
    private val queue = FakePendingSyncDataSource()
    private val scheduler = FakeSyncScheduler()
    private val timeProvider = FakeTimeProvider()
    private val serverClock = ServerClock(timeProvider, FakeServerClockOffsetStore())
    private val syncRemote = CountingRemoteSyncDataSource()

    private val repository = OfflineFirstActiveTimerRepository(
        remoteActiveTimerDataSource = remote,
        localProjectDataSource = local,
        deviceIdProvider = FakeDeviceIdProvider(),
        pendingSyncDataSource = queue,
        deltaSyncApplier = DeltaSyncApplier(
            remoteSyncDataSource = syncRemote,
            localProjectDataSource = local,
            projectRepository = OfflineFirstProjectRepository(
                localProjectDataSource = local,
                remoteProjectDataSource = FakeRemoteProjectDataSource(),
                pendingSyncDataSource = FakePendingSyncDataSource(),
                syncScheduler = FakeSyncScheduler(),
                applicationScope = CoroutineScope(Dispatchers.Unconfined),
                timeProvider = timeProvider
            ),
            syncCursorStore = FakeSyncCursorStore(),
            serverClock = serverClock,
            timeProvider = timeProvider
        ),
        syncScheduler = scheduler,
        serverClock = serverClock,
        timeProvider = timeProvider,
        applicationScope = CoroutineScope(Dispatchers.Unconfined)
    )

    private fun taskInterval(
        id: String = "i1",
        taskId: String = "t1",
        end: Instant? = null,
        device: String? = FakeDeviceIdProvider.THIS_DEVICE
    ) = TaskInterval(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = "p1",
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = end,
        durationMillis = end?.toEpochMilliseconds() ?: 0L,
        startedByDeviceId = device
    )

    private fun subTaskInterval(id: String = "si1") = SubTaskInterval(
        subTaskIntervalId = id,
        parentTaskIntervalId = "i1",
        parentSubTaskId = "s1",
        parentProjectId = "p1",
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = null,
        durationMillis = 0L
    )

    @Test
    fun aTaskStartNamesTheTaskIntervalAndStampsThisDevice() = runTest {
        repository.start(taskInterval())

        val sent = remote.starts.single()
        assertEquals("i1", sent.intervalId)
        assertEquals(ActiveTimerKind.TASK, sent.kind)
        assertNull(sent.parentSubTaskId)
        // Provenance is what lets the user's other devices tell a timer to adopt from one to reclaim.
        assertEquals(FakeDeviceIdProvider.THIS_DEVICE, sent.deviceId)
    }

    @Test
    fun aSubTaskStartNamesTheInnerIntervalAndItsEnclosingOne() = runTest {
        // Timing a subtask opens the parent task's interval too, but the timer the server
        // arbitrates is the inner one — that is what the user started.
        repository.start(taskInterval(), subTaskInterval())

        val sent = remote.starts.single()
        assertEquals("si1", sent.intervalId)
        assertEquals(ActiveTimerKind.SUB_TASK, sent.kind)
        assertEquals("s1", sent.parentSubTaskId)
        assertEquals("i1", sent.parentTaskIntervalId)
        // Still names the enclosing task, so the server can open it if it does not have it.
        assertEquals("t1", sent.parentTaskId)
    }

    @Test
    fun aSubTaskStartIsQueuedUnderTheSubTaskIntervalTypeAndItsSubTask() = runTest {
        // Queued as a task interval it would drain to the wrong endpoint, and the entityType
        // strings are persisted, so the mistake would outlive the upgrade that caused it.
        remote.nextStart = Result.Error(DataError.Remote.NO_INTERNET)

        repository.start(taskInterval(), subTaskInterval())

        val queued = queue.all().single()
        assertEquals("si1", queued.entityId)
        assertEquals(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL, queued.entityType)
        assertEquals("s1", queued.parentEntityId)
    }

    @Test
    fun aTakeoverClosesThePreviousIntervalFromTheEcho() = runTest {
        // The whole reason start and stop answer with `touched`: the device that superseded
        // another device's timer writes the closing row now, not at the next pull.
        val closedElsewhere = taskInterval(
            id = "i-other",
            taskId = "t-other",
            end = Instant.fromEpochMilliseconds(60_000),
            device = FakeDeviceIdProvider.OTHER_DEVICE
        )
        remote.nextStart = Result.Success(applied(taskIntervals = listOf(closedElsewhere)))

        repository.start(taskInterval())

        val stored = local.intervals["i-other"]
        assertEquals(Instant.fromEpochMilliseconds(60_000), stored?.endDateTimeUtc)
        // And the row still says which device opened it.
        assertEquals(FakeDeviceIdProvider.OTHER_DEVICE, stored?.startedByDeviceId)
    }

    @Test
    fun aRefusedStartResyncsInsteadOfGuessing() = runTest {
        // 409: this interval exists and is already closed, so the server will never reopen it.
        // Retrying repeats a request it has ruled on, and closing the optimistic local row needs
        // an end time nobody here has — so go and read the real state instead.
        local.intervals["i1"] = taskInterval()
        remote.nextStart = Result.Success(ActiveTimerChange.Rejected(active = null, serverNow = null))

        val result = repository.start(taskInterval())

        assertTrue(result is Result.Success)
        assertTrue(queue.all().isEmpty())
        assertEquals(1, syncRemote.pulls)
    }

    @Test
    fun anUnreachableServerQueuesTheStartOnTheIntervalQueue() = runTest {
        // Offline start has to degrade to exactly the old behaviour: the takeover is lost, the
        // tracked time is not.
        remote.nextStart = Result.Error(DataError.Remote.NO_INTERNET)

        repository.start(taskInterval())

        val queued = queue.all().single()
        assertEquals("i1", queued.entityId)
        assertEquals(PendingSyncOperation.ENTITY_INTERVAL, queued.entityType)
        assertEquals(PendingSyncOperation.OP_CREATE, queued.operationType)
        assertEquals(1, scheduler.scheduleCount)
    }

    @Test
    fun aStopNeverBanksADurationOntoTheTask() = runTest {
        // The sharpest double-count risk in the feature. The server's task row already carries a
        // foreign timer's time, so this repository closes intervals and nothing else — banking
        // stays with the caller, which is the only place that knows whose timer it was.
        local.tasks["t1"] = ProjectTask(
            projectTaskId = "t1",
            title = "already banked by the server",
            description = null,
            durationMillis = 60_000,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            parentProjectId = "p1",
            isTimerRunning = true
        )
        local.intervals["i1"] = taskInterval()
        remote.nextStop = Result.Success(
            applied(taskIntervals = listOf(taskInterval(end = Instant.fromEpochMilliseconds(60_000))))
        )

        repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

        assertEquals(Instant.fromEpochMilliseconds(60_000), local.intervals["i1"]?.endDateTimeUtc)
        // The minute is on the task row once, not twice.
        assertEquals(60_000, local.tasks["t1"]?.durationMillis)
    }

    @Test
    fun aRejectedStopResyncsInsteadOfClosingLocally() = runTest {
        // Another device moved the timer on. Closing here would need an end time nobody has, and
        // the interval may still be running somewhere — the guess StrandedTimerReconciler refuses.
        local.intervals["i1"] = taskInterval()
        remote.nextStop = Result.Success(ActiveTimerChange.Rejected(active = null, serverNow = null))

        val result = repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

        assertTrue(result is Result.Success)
        assertNull(local.intervals["i1"]?.endDateTimeUtc)
        assertEquals(1, syncRemote.pulls)
    }

    @Test
    fun anUnreachableServerQueuesTheStopAsAnUpdate() = runTest {
        remote.nextStop = Result.Error(DataError.Remote.REQUEST_TIMEOUT)

        repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

        val queued = queue.all().single()
        assertEquals(PendingSyncOperation.OP_UPDATE, queued.operationType)
        assertEquals(PendingSyncOperation.ENTITY_INTERVAL, queued.entityType)
    }

    @Test
    fun aSubTaskStopIsQueuedUnderTheSubTaskIntervalType() = runTest {
        // Queued as a task interval it would drain to the wrong endpoint, and the entityType
        // strings are persisted, so the mistake would outlive the upgrade that caused it.
        remote.nextStop = Result.Error(DataError.Remote.NO_INTERNET)

        repository.stop("si1", ActiveTimerKind.SUB_TASK, Instant.fromEpochMilliseconds(60_000))

        assertEquals(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL, queue.all().single().entityType)
    }

    @Test
    fun aPermanentErrorIsSurfacedRatherThanQueued() = runTest {
        // A queued retry would spend requests forever on one the server will keep refusing.
        remote.nextStart = Result.Error(DataError.Remote.BAD_REQUEST)

        val result = repository.start(taskInterval())

        assertEquals(Result.Error(DataError.Remote.BAD_REQUEST), result)
        assertTrue(queue.all().isEmpty())
    }

    @Test
    fun everyAnswerIsAClockSample() = runTest {
        // The timer subtracts startedAt from now, and startedAt may come from another device's
        // clock, so the offset matters more here than anywhere else.
        timeProvider.now = Instant.fromEpochMilliseconds(1_000)
        remote.nextStart = Result.Success(applied(serverNow = Instant.fromEpochMilliseconds(6_000)))

        repository.start(taskInterval())

        assertEquals(Instant.fromEpochMilliseconds(6_000), serverClock.now())
    }
}
