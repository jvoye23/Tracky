package com.jvcs.tracky.core.data.timer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.sync.DeltaSyncApplier
import com.jvcs.tracky.core.domain.sync.FakeSyncCursorStore
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.RemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.SyncRecency
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
import com.jvcs.tracky.features.projecttracker.data.FakeLocalProjectDataSource
import com.jvcs.tracky.features.projecttracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeRemoteProjectDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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

    override suspend fun stop(intervalId: String, endedAt: Instant): Result<ActiveTimerChange, DataError.Remote> {
        stops += intervalId to endedAt
        return nextStop ?: Result.Success(applied())
    }
}

private fun applied(taskIntervals: List<TaskInterval> = emptyList(), serverNow: Instant? = null) =
    ActiveTimerChange.Applied(null, taskIntervals, emptyList(), serverNow)

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
                tombstones = emptyList(),
            ),
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

    private val repository =
        OfflineFirstActiveTimerRepository(
            remoteActiveTimerDataSource = remote,
            localProjectDataSource = local,
            deviceIdProvider = FakeDeviceIdProvider(),
            pendingSyncDataSource = queue,
            deltaSyncApplier =
                DeltaSyncApplier(
                    remoteSyncDataSource = syncRemote,
                    localProjectDataSource = local,
                    projectRepository =
                        OfflineFirstProjectRepository(
                            localProjectDataSource = local,
                            remoteProjectDataSource = FakeRemoteProjectDataSource(),
                            pendingSyncDataSource = FakePendingSyncDataSource(),
                            syncScheduler = FakeSyncScheduler(),
                            applicationScope = CoroutineScope(Dispatchers.Unconfined),
                            timeProvider = timeProvider,
                        ),
                    syncCursorStore = FakeSyncCursorStore(),
                    serverClock = serverClock,
                    timeProvider = timeProvider,
                    syncRecency = SyncRecency(),
                ),
            syncScheduler = scheduler,
            serverClock = serverClock,
            timeProvider = timeProvider,
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
        )

    private fun taskInterval(
        id: String = "i1",
        taskId: String = "t1",
        end: Instant? = null,
        device: String? = FakeDeviceIdProvider.THIS_DEVICE,
    ) = TaskInterval(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = "p1",
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = end,
        durationMillis = end?.toEpochMilliseconds() ?: 0L,
        startedByDeviceId = device,
    )

    private fun subTaskInterval(id: String = "si1") =
        SubTaskInterval(
            subTaskIntervalId = id,
            parentTaskIntervalId = "i1",
            parentSubTaskId = "s1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = null,
            durationMillis = 0L,
        )

    @Test
    fun aTaskStartNamesTheTaskIntervalAndStampsThisDevice() =
        runTest {
            repository.start(taskInterval())

            val sent = remote.starts.single()
            assertThat(sent.intervalId).isEqualTo("i1")
            assertThat(sent.kind).isEqualTo(ActiveTimerKind.TASK)
            assertThat(sent.parentSubTaskId).isNull()
            // Provenance is what lets the user's other devices tell a timer to adopt from one to reclaim.
            assertThat(sent.deviceId).isEqualTo(FakeDeviceIdProvider.THIS_DEVICE)
        }

    @Test
    fun aSubTaskStartNamesTheInnerIntervalAndItsEnclosingOne() =
        runTest {
            // Timing a subtask opens the parent task's interval too, but the timer the server
            // arbitrates is the inner one — that is what the user started.
            repository.start(taskInterval(), subTaskInterval())

            val sent = remote.starts.single()
            assertThat(sent.intervalId).isEqualTo("si1")
            assertThat(sent.kind).isEqualTo(ActiveTimerKind.SUB_TASK)
            assertThat(sent.parentSubTaskId).isEqualTo("s1")
            assertThat(sent.parentTaskIntervalId).isEqualTo("i1")
            // Still names the enclosing task, so the server can open it if it does not have it.
            assertThat(sent.parentTaskId).isEqualTo("t1")
        }

    @Test
    fun aSubTaskStartIsQueuedUnderTheSubTaskIntervalTypeAndItsSubTask() =
        runTest {
            // Queued as a task interval it would drain to the wrong endpoint, and the entityType
            // strings are persisted, so the mistake would outlive the upgrade that caused it.
            remote.nextStart = Result.Error(DataError.Remote.NO_INTERNET)

            repository.start(taskInterval(), subTaskInterval())

            val queued = queue.all().single()
            assertThat(queued.entityId).isEqualTo("si1")
            assertThat(queued.entityType).isEqualTo(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL)
            assertThat(queued.parentEntityId).isEqualTo("s1")
        }

    @Test
    fun aTakeoverClosesThePreviousIntervalFromTheEcho() =
        runTest {
            // The whole reason start and stop answer with `touched`: the device that superseded
            // another device's timer writes the closing row now, not at the next pull.
            val closedElsewhere =
                taskInterval(
                    id = "i-other",
                    taskId = "t-other",
                    end = Instant.fromEpochMilliseconds(60_000),
                    device = FakeDeviceIdProvider.OTHER_DEVICE,
                )
            remote.nextStart = Result.Success(applied(taskIntervals = listOf(closedElsewhere)))

            repository.start(taskInterval())

            val stored = local.intervals["i-other"]
            assertThat(stored?.endDateTimeUtc).isEqualTo(Instant.fromEpochMilliseconds(60_000))
            // And the row still says which device opened it.
            assertThat(stored?.startedByDeviceId).isEqualTo(FakeDeviceIdProvider.OTHER_DEVICE)
        }

    @Test
    fun aRefusedStartResyncsInsteadOfGuessing() =
        runTest {
            // 409: this interval exists and is already closed, so the server will never reopen it.
            // Retrying repeats a request it has ruled on, and closing the optimistic local row needs
            // an end time nobody here has — so go and read the real state instead.
            local.intervals["i1"] = taskInterval()
            remote.nextStart = Result.Success(ActiveTimerChange.Rejected(active = null, serverNow = null))

            val result = repository.start(taskInterval())

            assertThat(result is Result.Success).isTrue()
            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(syncRemote.pulls).isEqualTo(1)
        }

    @Test
    fun anUnreachableServerQueuesTheStartOnTheIntervalQueue() =
        runTest {
            // Offline start has to degrade to exactly the old behaviour: the takeover is lost, the
            // tracked time is not.
            remote.nextStart = Result.Error(DataError.Remote.NO_INTERNET)

            repository.start(taskInterval())

            val queued = queue.all().single()
            assertThat(queued.entityId).isEqualTo("i1")
            assertThat(queued.entityType).isEqualTo(PendingSyncOperation.ENTITY_INTERVAL)
            assertThat(queued.operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
            assertThat(scheduler.scheduleCount).isEqualTo(1)
        }

    @Test
    fun aStopNeverBanksADurationOntoTheTask() =
        runTest {
            // The sharpest double-count risk in the feature. The server's task row already carries a
            // foreign timer's time, so this repository closes intervals and nothing else — banking
            // stays with the caller, which is the only place that knows whose timer it was.
            local.tasks["t1"] =
                ProjectTask(
                    projectTaskId = "t1",
                    title = "already banked by the server",
                    description = null,
                    durationMillis = 60_000,
                    startDateTimeUtc = Instant.fromEpochMilliseconds(0),
                    parentProjectId = "p1",
                    isTimerRunning = true,
                )
            local.intervals["i1"] = taskInterval()
            remote.nextStop =
                Result.Success(
                    applied(taskIntervals = listOf(taskInterval(end = Instant.fromEpochMilliseconds(60_000)))),
                )

            repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

            assertThat(local.intervals["i1"]?.endDateTimeUtc).isEqualTo(Instant.fromEpochMilliseconds(60_000))
            // The minute is on the task row once, not twice.
            assertThat(local.tasks["t1"]?.durationMillis).isEqualTo(60_000)
        }

    @Test
    fun aRejectedStopResyncsInsteadOfClosingLocally() =
        runTest {
            // Another device moved the timer on. Closing here would need an end time nobody has, and
            // the interval may still be running somewhere — the guess StrandedTimerReconciler refuses.
            local.intervals["i1"] = taskInterval()
            remote.nextStop = Result.Success(ActiveTimerChange.Rejected(active = null, serverNow = null))

            val result = repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

            assertThat(result is Result.Success).isTrue()
            assertThat(local.intervals["i1"]?.endDateTimeUtc).isNull()
            assertThat(syncRemote.pulls).isEqualTo(1)
        }

    @Test
    fun anUnreachableServerQueuesTheStopAsAnUpdate() =
        runTest {
            remote.nextStop = Result.Error(DataError.Remote.REQUEST_TIMEOUT)

            repository.stop("i1", ActiveTimerKind.TASK, Instant.fromEpochMilliseconds(60_000))

            val queued = queue.all().single()
            assertThat(queued.operationType).isEqualTo(PendingSyncOperation.OP_UPDATE)
            assertThat(queued.entityType).isEqualTo(PendingSyncOperation.ENTITY_INTERVAL)
        }

    @Test
    fun aSubTaskStopIsQueuedUnderTheSubTaskIntervalType() =
        runTest {
            // Queued as a task interval it would drain to the wrong endpoint, and the entityType
            // strings are persisted, so the mistake would outlive the upgrade that caused it.
            remote.nextStop = Result.Error(DataError.Remote.NO_INTERNET)

            repository.stop("si1", ActiveTimerKind.SUB_TASK, Instant.fromEpochMilliseconds(60_000))

            assertThat(queue.all().single().entityType).isEqualTo(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL)
        }

    @Test
    fun aPermanentErrorIsSurfacedRatherThanQueued() =
        runTest {
            // A queued retry would spend requests forever on one the server will keep refusing.
            remote.nextStart = Result.Error(DataError.Remote.BAD_REQUEST)

            val result = repository.start(taskInterval())

            assertThat(result).isEqualTo(Result.Error(DataError.Remote.BAD_REQUEST))
            assertThat(queue.all().isEmpty()).isTrue()
        }

    @Test
    fun everyAnswerIsAClockSample() =
        runTest {
            // The timer subtracts startedAt from now, and startedAt may come from another device's
            // clock, so the offset matters more here than anywhere else.
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            remote.nextStart = Result.Success(applied(serverNow = Instant.fromEpochMilliseconds(6_000)))

            repository.start(taskInterval())

            assertThat(serverClock.now()).isEqualTo(Instant.fromEpochMilliseconds(6_000))
        }
}
