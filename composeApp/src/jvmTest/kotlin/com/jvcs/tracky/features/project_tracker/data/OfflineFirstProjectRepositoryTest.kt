@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.serverWinsOnPull
import com.jvcs.tracky.core.domain.sync.serverWinsOnPullForInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

class OfflineFirstProjectRepositoryTest {

    private fun repo(
        local: FakeLocalProjectDataSource,
        remote: FakeRemoteProjectDataSource,
        queue: FakePendingSyncDataSource,
        scheduler: FakeSyncScheduler,
        time: FakeTimeProvider = FakeTimeProvider()
    ) = OfflineFirstProjectRepository(
        localProjectDataSource = local,
        remoteProjectDataSource = remote,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        timeProvider = time
    )

    private fun project(id: String) = Project(
        projectId = id,
        title = "title-$id",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null
    )

    @Test
    fun upsertProject_queuesCreate_whenRemoteOffline() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()

        val result = repo(local, remote, queue, scheduler).upsertProject(project("p1"))

        // User sees success because the local write succeeded.
        assertTrue(result is Result.Success)
        assertNotNull(local.projects["p1"])

        val ops = queue.all()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.ENTITY_PROJECT, ops[0].entityType)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
        assertTrue(scheduler.scheduleCount > 0)
    }

    @Test
    fun syncPendingOperations_pushesQueuedCreate_andClearsQueue() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        val repository = repo(local, remote, queue, scheduler)

        repository.upsertProject(project("p1")) // queued while offline
        remote.failWith = null                  // back online

        repository.syncPendingOperations()

        assertTrue(queue.all().isEmpty())
        assertTrue(remote.postedProjectIds.contains("p1"))
    }

    /** Seeds three projects already carrying a contiguous order 0,1,2. */
    private fun FakeLocalProjectDataSource.seedOrderedProjects() {
        projects["p1"] = project("p1").copy(sortIndex = 0)
        projects["p2"] = project("p2").copy(sortIndex = 1)
        projects["p3"] = project("p3").copy(sortIndex = 2)
    }

    @Test
    fun reorderProjects_writesShiftedIndices_inASingleLocalWrite() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource() // online
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        // Move p3 to the middle -> new order p1, p3, p2.
        val result = repo(local, remote, queue, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        assertTrue(result is Result.Success)
        assertEquals(0L, local.projects["p1"]!!.sortIndex)
        assertEquals(1L, local.projects["p3"]!!.sortIndex)
        assertEquals(2L, local.projects["p2"]!!.sortIndex)
        // The whole gesture is one transaction, carrying only the two cards that actually moved.
        assertEquals(1, local.sortIndexWrites.size)
        assertEquals(mapOf("p3" to 1L, "p2" to 2L), local.sortIndexWrites.single())
    }

    @Test
    fun reorderProjects_makesExactlyOneNetworkCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        repo(local, remote, queue, scheduler).reorderProjects(listOf("p1", "p3", "p2"))

        // One drag must not fan out into one PUT per shifted card.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p3" to 1L, "p2" to 2L), remote.reorderCalls.single())
        assertTrue(remote.updatedProjectIds.isEmpty())
    }

    @Test
    fun reorderProjects_whenNothingMoved_writesNothing() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, queue, scheduler)
            .reorderProjects(listOf("p1", "p2", "p3")) // already the stored order

        assertTrue(result is Result.Success)
        assertTrue(local.sortIndexWrites.isEmpty())
        assertTrue(remote.reorderCalls.isEmpty())
    }

    @Test
    fun reorderProjects_whenLocalWriteFails_doesNotHitNetwork_andLeavesOrderUntouched() = runBlocking {
        val local = FakeLocalProjectDataSource().apply { failSortIndexWrite = true }
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, queue, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        assertTrue(result is Result.Error)
        assertTrue(remote.reorderCalls.isEmpty())
        // Nothing landed: the old order is intact rather than half-applied.
        assertEquals(0L, local.projects["p1"]!!.sortIndex)
        assertEquals(1L, local.projects["p2"]!!.sortIndex)
        assertEquals(2L, local.projects["p3"]!!.sortIndex)
    }

    @Test
    fun reorderProjects_whenOffline_queuesOneOrderOp_andReportsSuccess() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, queue, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        // User sees success because the local write succeeded.
        assertTrue(result is Result.Success)
        assertEquals(1L, local.projects["p3"]!!.sortIndex)

        val ops = queue.all()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.ENTITY_PROJECT_ORDER, ops[0].entityType)
        assertEquals(PendingSyncOperation.OP_UPDATE, ops[0].operationType)
        assertTrue(scheduler.scheduleCount > 0)
    }

    @Test
    fun reorderProjects_twiceWhileOffline_stillQueuesOneOrderOp() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()
        val repository = repo(local, remote, queue, scheduler)

        repository.reorderProjects(listOf("p1", "p3", "p2"))
        repository.reorderProjects(listOf("p3", "p2", "p1"))

        // The order is a single piece of state — two drags collapse into one queued push.
        assertEquals(1, queue.all().size)
    }

    @Test
    fun syncPendingOperations_drainsOrderOp_withOneBatchCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()
        val repository = repo(local, remote, queue, scheduler)

        repository.reorderProjects(listOf("p1", "p3", "p2")) // queued while offline
        remote.failWith = null                               // back online
        remote.reorderCalls.clear()

        repository.syncPendingOperations()

        assertTrue(queue.all().isEmpty())
        // The queued row is rebuilt from current local state: the full order, in one call.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p1" to 0L, "p3" to 1L, "p2" to 2L), remote.reorderCalls.single())
    }

    @Test
    fun reorderProjects_skipsIdsMissingLocally() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        // "ghost" was deleted on another device but is still in the mirror list the UI committed.
        val result = repo(local, remote, queue, scheduler)
            .reorderProjects(listOf("ghost", "p1", "p2", "p3"))

        assertTrue(result is Result.Success)
        assertFalse(local.sortIndexWrites.single().containsKey("ghost"))
        assertEquals(mapOf("p1" to 1L, "p2" to 2L, "p3" to 3L), local.sortIndexWrites.single())
    }

    /** Pinned section p1,p2 (0,1) and Other section p3,p4,p5 (0,1,2) — both numbered from 0. */
    private fun FakeLocalProjectDataSource.seedTwoSections() {
        projects["p1"] = project("p1").copy(isPinned = true, sortIndex = 0)
        projects["p2"] = project("p2").copy(isPinned = true, sortIndex = 1)
        projects["p3"] = project("p3").copy(sortIndex = 0)
        projects["p4"] = project("p4").copy(sortIndex = 1)
        projects["p5"] = project("p5").copy(sortIndex = 2)
    }

    private fun FakeLocalProjectDataSource.sectionOrder(isPinned: Boolean) =
        projects.values
            .filter { it.isPinned == isPinned }
            .sortedBy { it.sortIndex }
            .map { it.projectId }

    @Test
    fun setProjectsPinned_putsPinnedProjectOnTopOfItsNewSection() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        // p4 sits at index 1 in Other, where p2 already sits at 1 in Pinned. Flipping the flag alone
        // would leave them sharing an index and let the creation date decide the order.
        val result = repo(local, remote, queue, scheduler).setProjectsPinned(listOf("p4"), isPinned = true)

        assertTrue(result is Result.Success)
        assertTrue(local.projects["p4"]!!.isPinned)
        assertEquals(listOf("p4", "p1", "p2"), local.sectionOrder(isPinned = true))
        assertEquals(listOf(0L, 1L, 2L), listOf("p4", "p1", "p2").map { local.projects[it]!!.sortIndex })
    }

    @Test
    fun setProjectsPinned_keepsRelativeOrder_whenPinningSeveralAtOnce() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        // Selection order is a Set's, so the repository must fall back on the stored order: p3 (0)
        // before p5 (2).
        repo(local, remote, queue, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

        assertEquals(listOf("p3", "p5", "p1", "p2"), local.sectionOrder(isPinned = true))
    }

    @Test
    fun setProjectsPinned_unpinning_putsProjectOnTopOfOther() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        repo(local, remote, queue, scheduler).setProjectsPinned(listOf("p1"), isPinned = false)

        assertFalse(local.projects["p1"]!!.isPinned)
        assertEquals(listOf("p1", "p3", "p4", "p5"), local.sectionOrder(isPinned = false))
        assertEquals(listOf("p2"), local.sectionOrder(isPinned = true))
    }

    @Test
    fun setProjectsPinned_reindexesTheSectionInOneBatchCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        repo(local, remote, queue, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

        // One gesture, one /sort request — not one per shifted card. p3 already sat at 0 and stays
        // there, so it is not part of the write.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p5" to 1L, "p1" to 2L, "p2" to 3L), remote.reorderCalls.single())
        assertEquals(1, local.sortIndexWrites.size)
    }

    @Test
    fun setProjectsPinned_skipsIdsMissingLocally() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        val result = repo(local, remote, queue, scheduler)
            .setProjectsPinned(listOf("ghost"), isPinned = true)

        assertTrue(result is Result.Success)
        assertEquals(listOf("p1", "p2"), local.sectionOrder(isPinned = true))
        assertTrue(remote.reorderCalls.isEmpty())
    }

    @Test
    fun deleteProject_droppedLocally_whenStillPendingCreate_neverHitsServer() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        val repository = repo(local, remote, queue, scheduler)

        repository.upsertProject(project("p1")) // queued CREATE (never reached server)
        repository.deleteProject("p1")

        assertNull(local.projects["p1"])
        assertTrue(queue.all().isEmpty())
        assertFalse(remote.deletedProjectIds.contains("p1"))
    }

    @Test
    fun upsertProject_stampsUpdatedAt_fromTheInjectedClock() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(1_234_567))

        repo(local, remote, queue, scheduler, time).upsertProject(project("p1"))

        assertEquals(time.now, remote.postedProjects.single().ownUpdatedAt)
    }

    @Test
    fun reorderProjects_stampsLocalAndRemote_withTheSameInstant() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val queue = FakePendingSyncDataSource()
        val scheduler = FakeSyncScheduler()
        // Every read of the clock advances it, so a second read would produce a different stamp.
        val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(1_000), advanceOnReadMillis = 1)
        local.seedTwoSections()

        repo(local, remote, queue, scheduler, time).reorderProjects(listOf("p2", "p1"))

        assertEquals(Instant.fromEpochMilliseconds(1_000), local.sortIndexWriteTimestamps.single())
        assertEquals(Instant.fromEpochMilliseconds(1_000), remote.reorderTimestamps.single())
    }

    // --- Pull path --------------------------------------------------------------------------------

    private fun serverTask(
        taskId: String,
        projectId: String,
        updatedAt: Long?,
        intervals: List<TaskInterval> = emptyList()
    ) = ProjectTask(
        projectTaskId = taskId,
        title = "task-$taskId",
        durationMillis = 0,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = projectId,
        isTimerRunning = false,
        intervals = intervals,
        ownUpdatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    private fun serverInterval(
        intervalId: String,
        taskId: String,
        end: Long?,
        projectId: String = "p1",
    ) = TaskInterval(
        intervalId = intervalId,
        parentTaskId = taskId,
        parentProjectId = projectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = end?.let { Instant.fromEpochMilliseconds(it) },
        durationMillis = end ?: 0L,
    )

    @Test
    fun fetchProjects_persistsNestedTasksAndIntervals() = runBlocking {
        val f = Quad(FakeLocalProjectDataSource(), FakeRemoteProjectDataSource(), FakePendingSyncDataSource(), FakeSyncScheduler())
        f.remote.projectsToReturn = listOf(
            project("p1").copy(
                ownUpdatedAt = Instant.fromEpochMilliseconds(100),
                projectTasks = listOf(
                    serverTask("t1", "p1", updatedAt = 100, intervals = listOf(serverInterval("i1", "t1", end = 60_000)))
                )
            )
        )

        f.repository().fetchProjects()

        // This is the fresh-install case: tracked time has to come back, not just the project row.
        assertNotNull(f.local.projects["p1"])
        assertNotNull(f.local.tasks["t1"])
        assertEquals(60_000L, f.local.intervals.getValue("i1").durationMillis)
    }

    @Test
    fun fetchProjects_keepsALocalTaskEditThatIsNewerThanTheServer() = runBlocking {
        val f = Quad(FakeLocalProjectDataSource(), FakeRemoteProjectDataSource(), FakePendingSyncDataSource(), FakeSyncScheduler())
        f.local.tasks["t1"] = serverTask("t1", "p1", updatedAt = 500).copy(title = "edited offline")
        f.remote.projectsToReturn = listOf(
            project("p1").copy(projectTasks = listOf(serverTask("t1", "p1", updatedAt = 100)))
        )

        f.repository().fetchProjects()

        // Overwriting here would also feed the stale title back to the server on the next drain.
        assertEquals("edited offline", f.local.tasks.getValue("t1").title)
    }

    @Test
    fun fetchProjects_doesNotCloseAnIntervalThatIsStillRunningLocally() = runBlocking {
        val f = Quad(FakeLocalProjectDataSource(), FakeRemoteProjectDataSource(), FakePendingSyncDataSource(), FakeSyncScheduler())
        f.local.intervals["i1"] = serverInterval("i1", "t1", end = null) // timer running on this device
        f.remote.projectsToReturn = listOf(
            project("p1").copy(
                projectTasks = listOf(
                    serverTask("t1", "p1", updatedAt = 100, intervals = listOf(serverInterval("i1", "t1", end = 60_000)))
                )
            )
        )

        f.repository().fetchProjects()

        assertNull(f.local.intervals.getValue("i1").endDateTimeUtc)
    }

    @Test
    fun fetchProjects_leavesLocalRowsTheServerDoesNotKnowAbout() = runBlocking {
        val f = Quad(FakeLocalProjectDataSource(), FakeRemoteProjectDataSource(), FakePendingSyncDataSource(), FakeSyncScheduler())
        f.local.tasks["local-only"] = serverTask("local-only", "p1", updatedAt = null)
        f.local.intervals["i-local"] = serverInterval("i-local", "local-only", end = 1_000)
        f.remote.projectsToReturn = listOf(project("p1").copy(projectTasks = emptyList()))

        f.repository().fetchProjects()

        // Created offline and still queued for upload — a pull must never delete these.
        assertTrue(f.local.tasks.containsKey("local-only"))
        assertTrue(f.local.intervals.containsKey("i-local"))
    }

    // --- Task intervals ---------------------------------------------------------------------------

    /** Local + remote wired together with one task "t1" under project "p1", timer stopped. */
    private fun intervalFixture(): Quad {
        val local = FakeLocalProjectDataSource().apply { seedTask("t1", "p1") }
        return Quad(local, FakeRemoteProjectDataSource(), FakePendingSyncDataSource(), FakeSyncScheduler())
    }

    private data class Quad(
        val local: FakeLocalProjectDataSource,
        val remote: FakeRemoteProjectDataSource,
        val queue: FakePendingSyncDataSource,
        val scheduler: FakeSyncScheduler
    )

    private fun Quad.repository() = repo(local, remote, queue, scheduler)

    @Test
    fun startTask_postsTheNewIntervalToTheTasksRoute() = runBlocking {
        val f = intervalFixture()

        f.repository().startTask("t1")

        assertEquals(listOf("i1"), f.remote.postedIntervalIds)
        // The route is built from the interval's own parentProjectId — no task lookup involved.
        assertEquals(listOf("p1/t1"), f.remote.intervalRoutes)
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun stopTask_putsTheClosedInterval() = runBlocking {
        val f = intervalFixture()
        val repository = f.repository()

        repository.startTask("t1")
        f.local.clock = Instant.fromEpochMilliseconds(70_000) // 60s after the default start
        repository.stopTask("t1")

        assertEquals(listOf("i1"), f.remote.updatedIntervalIds)
        assertEquals(60_000L, f.local.intervals.getValue("i1").durationMillis)
    }

    @Test
    fun startTask_queuesTheInterval_whenOffline() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET

        f.repository().startTask("t1")

        // Local write stands regardless — the user keeps tracking time.
        assertNotNull(f.local.intervals["i1"])

        val ops = f.queue.all()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.ENTITY_INTERVAL, ops[0].entityType)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
        assertEquals("i1", ops[0].entityId)
        // parentEntityId carries the TASK id for intervals; a queued DELETE has no local row left
        // to read parentProjectId from, so it resolves the project through the task at drain time.
        assertEquals("t1", ops[0].parentEntityId)
        assertTrue(f.scheduler.scheduleCount > 0)
    }

    /**
     * The task was created offline, so the server does not know it yet and answers 404. Queueing
     * (rather than dropping) is what lets the FIFO drain push the task first and the interval after.
     */
    @Test
    fun startTask_queuesTheInterval_whenTheTaskIsNotOnTheServerYet() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NOT_FOUND

        f.repository().startTask("t1")

        val ops = f.queue.all()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.ENTITY_INTERVAL, ops[0].entityType)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
    }

    /** A 409 means the POST already landed and only its response was lost. */
    @Test
    fun startTask_retriesAsUpdate_whenTheServerReportsADuplicate() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.CONFLICT

        f.repository().startTask("t1")

        assertEquals(listOf("i1"), f.remote.updatedIntervalIds)
        assertTrue(f.remote.postedIntervalIds.isEmpty())
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun syncPendingOperations_pushesQueuedInterval_andClearsQueue() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET
        val repository = f.repository()

        repository.startTask("t1")            // queued while offline
        f.remote.intervalPostFailWith = null  // back online

        repository.syncPendingOperations()

        assertEquals(listOf("i1"), f.remote.postedIntervalIds)
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun syncPendingOperations_dropsQueuedInterval_whenItWasDeletedLocally() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET
        val repository = f.repository()

        repository.startTask("t1")
        f.local.intervals.remove("i1")        // gone before the queue drained
        f.remote.intervalPostFailWith = null

        repository.syncPendingOperations()

        assertTrue(f.remote.postedIntervalIds.isEmpty())
        assertTrue(f.queue.all().isEmpty()) // dropped, not retried forever
    }

    @Test
    fun syncPendingOperations_dropsQueuedInterval_whenItsTaskIsGone() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET
        val repository = f.repository()

        repository.startTask("t1")
        // Deleting the task cascades to its intervals, so the queued op has nothing left to push.
        f.local.deleteProjectTask("t1")
        f.remote.intervalPostFailWith = null

        repository.syncPendingOperations()

        assertTrue(f.remote.postedIntervalIds.isEmpty())
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun syncPendingOperations_retriesQueuedInterval_whenStillOffline() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET
        val repository = f.repository()

        repository.startTask("t1")
        repository.syncPendingOperations() // still offline

        assertEquals(1, f.queue.all().size) // left queued for the next attempt
    }

    @Test
    fun deleteTaskInterval_deletesLocallyAndRemotely() = runBlocking {
        val f = intervalFixture()
        val repository = f.repository()
        repository.startTask("t1")

        repository.deleteTaskInterval("i1")

        assertNull(f.local.intervals["i1"])
        assertEquals(listOf("i1"), f.remote.deletedIntervalIds)
    }

    @Test
    fun deleteTaskInterval_dropsThePendingCreate_andSkipsTheServer() = runBlocking {
        val f = intervalFixture()
        f.remote.intervalPostFailWith = DataError.Remote.NO_INTERNET
        val repository = f.repository()

        repository.startTask("t1")            // create is queued, never reached the server
        repository.deleteTaskInterval("i1")

        // Nothing to delete server-side — the interval never got there.
        assertTrue(f.remote.deletedIntervalIds.isEmpty())
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun deleteTaskInterval_queuesTheDelete_whenOffline() = runBlocking {
        val f = intervalFixture()
        val repository = f.repository()
        repository.startTask("t1")                                        // succeeds online
        f.remote.intervalDeleteFailWith = DataError.Remote.NO_INTERNET

        repository.deleteTaskInterval("i1")

        val ops = f.queue.all()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.ENTITY_INTERVAL, ops[0].entityType)
        assertEquals(PendingSyncOperation.OP_DELETE, ops[0].operationType)
        assertEquals("t1", ops[0].parentEntityId)
    }

    @Test
    fun syncPendingOperations_pushesQueuedIntervalDelete() = runBlocking {
        val f = intervalFixture()
        val repository = f.repository()
        repository.startTask("t1")
        f.remote.intervalDeleteFailWith = DataError.Remote.NO_INTERNET
        repository.deleteTaskInterval("i1")

        f.remote.intervalDeleteFailWith = null
        repository.syncPendingOperations()

        // The interval row is gone locally, so the delete has to survive on the queued task id alone.
        assertEquals(listOf("i1"), f.remote.deletedIntervalIds)
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun upsertTaskInterval_updatesAnExistingIntervalRatherThanCreatingIt() = runBlocking {
        val f = intervalFixture()
        val repository = f.repository()
        repository.startTask("t1")

        repository.upsertTaskInterval(
            f.local.intervals.getValue("i1").copy(durationMillis = 5_000)
        )

        assertEquals(listOf("i1"), f.remote.updatedIntervalIds)
        assertEquals(listOf("i1"), f.remote.postedIntervalIds) // only the original startTask create
    }
}

// --- Fakes ----------------------------------------------------------------------------------------

private class FakeLocalProjectDataSource : LocalProjectDataSource {
    val projects = linkedMapOf<String, Project>()
    val upsertedProjectIds = mutableListOf<String>()
    /** One entry per updateSortIndices call, so tests can assert a reorder is a single write. */
    val sortIndexWrites = mutableListOf<Map<String, Long>>()
    val sortIndexWriteTimestamps = mutableListOf<Instant>()
    var failSortIndexWrite = false
    private val projectsFlow = MutableStateFlow<List<Project>>(emptyList())

    private fun emit() { projectsFlow.value = projects.values.toList() }

    override fun getProjects(): Flow<List<Project>> = projectsFlow
    override fun getActiveProjects(): Flow<List<Project>> =
        projectsFlow.map { list -> list.filter { !it.isArchived && !it.isFinished && it.trashedAt == null } }
    override suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local> = Result.Success(
        projects.values.filter { it.isPinned && !it.isArchived && !it.isFinished && it.trashedAt == null }
    )
    override fun getArchivedProjects(): Flow<List<Project>> =
        projectsFlow.map { list -> list.filter { it.isArchived && it.trashedAt == null } }
    override fun getTrashedProjects(): Flow<List<Project>> =
        projectsFlow.map { list -> list.filter { it.trashedAt != null } }
    override suspend fun getExpiredTrashedProjectIds(cutoff: Instant): Result<List<String>, DataError.Local> =
        Result.Success(projects.values.filter { it.trashedAt != null && it.trashedAt!! < cutoff }.map { it.projectId })
    override suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(projects[projectId])
    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(projects[projectId])

    override suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local> =
        Result.Success(projects.mapValues { (_, project) -> project.sortIndex })

    override suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Local> {
        // All or nothing, like the DAO transaction it stands in for.
        if (failSortIndexWrite) return Result.Error(DataError.Local.DISK_FULL)
        sortIndexWrites += indices
        sortIndexWriteTimestamps += updatedAt
        indices.forEach { (id, index) ->
            projects[id]?.let { projects[id] = it.copy(sortIndex = index, ownUpdatedAt = updatedAt) }
        }
        emit()
        return Result.Success(Unit)
    }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError.Local> {
        upsertedProjectIds += project.projectId
        projects[project.projectId] = project; emit(); return Result.Success(Unit)
    }

    /**
     * Stands in for ProjectDao.upsertServerTree: writes the whole tree and applies the same merge
     * rules, so a pull in a test can clobber (or refuse to clobber) local state the way it would
     * against a real database.
     */
    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local> {
        projects.forEach { incoming ->
            if (serverWinsOnPull(this.projects[incoming.projectId]?.ownUpdatedAt?.toEpochMilliseconds(),
                    incoming.ownUpdatedAt?.toEpochMilliseconds())) {
                this.projects[incoming.projectId] = incoming
            }
        }
        val incomingTasks = projects.flatMap { it.projectTasks.orEmpty() }
        incomingTasks.forEach { incoming ->
            if (serverWinsOnPull(tasks[incoming.projectTaskId]?.ownUpdatedAt?.toEpochMilliseconds(),
                    incoming.ownUpdatedAt?.toEpochMilliseconds())) {
                tasks[incoming.projectTaskId] = incoming
            }
        }
        incomingTasks.flatMap { it.intervals }.forEach { incoming ->
            val local = intervals[incoming.intervalId]
            if (local == null || serverWinsOnPullForInterval(local.endDateTimeUtc?.toEpochMilliseconds())) {
                intervals[incoming.intervalId] = incoming
            }
        }
        emit()
        return Result.Success(Unit)
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local> {
        tasks[projectTask.projectTaskId] = projectTask
        return Result.Success(Unit)
    }

    // Room cascades projects -> project_records -> task_intervals, so the fake has to as well;
    // otherwise the queue-draining tests would see rows the real database could never hold.
    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> {
        projects.remove(projectId)
        tasks.values.filter { it.parentProjectId == projectId }.map { it.projectTaskId }
            .forEach { deleteProjectTask(it) }
        emit()
        return Result.Success(Unit)
    }
    override suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local> {
        tasks.remove(taskId)
        intervals.values.filter { it.parentTaskId == taskId }.map { it.intervalId }
            .forEach { intervals.remove(it) }
        return Result.Success(Unit)
    }
    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> {
        projects.clear(); tasks.clear(); intervals.clear(); emit()
        return Result.Success(Unit)
    }
    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError.Local> =
        Result.Success(Unit)

    // --- Task / interval state -------------------------------------------------------------------
    val tasks = linkedMapOf<String, ProjectTask>()
    val intervals = linkedMapOf<String, TaskInterval>()
    val upsertedIntervalIds = mutableListOf<String>()
    val deletedIntervalIds = mutableListOf<String>()
    /** startTask generates the id in production; pin it here so tests can assert on it. */
    var nextIntervalId = "i1"
    var clock: Instant = Instant.fromEpochMilliseconds(10_000)

    fun seedTask(taskId: String, projectId: String) {
        tasks[taskId] = ProjectTask(
            projectTaskId = taskId,
            title = "task-$taskId",
            durationMillis = 0,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            parentProjectId = projectId,
            isTimerRunning = false,
        )
    }

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(taskWithIntervals(taskId))

    override suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local> =
        Result.Success(taskWithIntervals(taskId))

    private fun taskWithIntervals(taskId: String) =
        tasks[taskId]?.copy(intervals = intervals.values.filter { it.parentTaskId == taskId })

    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local> {
        upsertedIntervalIds += interval.intervalId
        intervals[interval.intervalId] = interval
        return Result.Success(Unit)
    }

    override suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local> =
        Result.Success(openIntervalOf(taskId))

    private fun openIntervalOf(taskId: String) =
        intervals.values.firstOrNull { it.parentTaskId == taskId && it.endDateTimeUtc == null }

    override suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local> =
        Result.Success(intervals[intervalId])

    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local> {
        deletedIntervalIds += intervalId
        intervals.remove(intervalId)
        return Result.Success(Unit)
    }

    override suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local> {
        // Mirrors the Room implementation: the interval carries its project, so an unknown task
        // has nothing to hang the new row off.
        val task = tasks[taskId] ?: return Result.Error(DataError.Local.NOT_FOUND)
        val interval = TaskInterval(
            intervalId = nextIntervalId,
            parentTaskId = taskId,
            parentProjectId = task.parentProjectId,
            startDateTimeUtc = clock,
            endDateTimeUtc = null,
            durationMillis = 0L,
        )
        intervals[interval.intervalId] = interval
        tasks[taskId]?.let { tasks[taskId] = it.copy(isTimerRunning = true) }
        return Result.Success(interval)
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local> {
        val open = openIntervalOf(taskId)
        tasks[taskId]?.let { tasks[taskId] = it.copy(isTimerRunning = false) }
        if (open == null) return Result.Success(null)
        val closed = open.copy(
            endDateTimeUtc = clock,
            durationMillis = (clock - open.startDateTimeUtc).inWholeMilliseconds,
        )
        intervals[closed.intervalId] = closed
        return Result.Success(closed)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local> =
        Result.Success(Unit)
}

private class FakeRemoteProjectDataSource : RemoteProjectDataSource {
    var failWith: DataError.Remote? = null
    val postedProjects = mutableListOf<Project>()
    val postedProjectIds = mutableListOf<String>()
    val updatedProjectIds = mutableListOf<String>()
    val deletedProjectIds = mutableListOf<String>()
    /** One entry per reorderProjects call, so tests can assert a drag is a single network call. */
    val reorderCalls = mutableListOf<Map<String, Long>>()
    val reorderTimestamps = mutableListOf<Instant>()

    /** What GET /api/projects hands back — the whole tree, tasks and intervals included. */
    var projectsToReturn: List<Project> = emptyList()

    override suspend fun getProjects(): Result<List<Project>, DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(projectsToReturn)

    override suspend fun postProject(project: Project): Result<Project, DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        postedProjects += project
        postedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        updatedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        reorderCalls += indices
        reorderTimestamps += updatedAt
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        deletedProjectIds += projectId
        return Result.Success(Unit)
    }

    override suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(emptyList())

    override suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(task)

    override suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(task)

    override suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(Unit)

    // --- Intervals -------------------------------------------------------------------------------
    // Kept on separate failure switches from `failWith` so a test can fail an interval call while
    // the task/project calls around it still succeed.
    var intervalPostFailWith: DataError.Remote? = null
    var intervalUpdateFailWith: DataError.Remote? = null
    var intervalDeleteFailWith: DataError.Remote? = null
    val postedIntervalIds = mutableListOf<String>()
    val updatedIntervalIds = mutableListOf<String>()
    val deletedIntervalIds = mutableListOf<String>()
    /** "<projectId>/<taskId>" per interval call, so tests can assert the route was resolved. */
    val intervalRoutes = mutableListOf<String>()

    override suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote> {
        intervalRoutes += "$projectId/$taskId"
        intervalPostFailWith?.let { return Result.Error(it) }
        postedIntervalIds += interval.intervalId
        return Result.Success(interval)
    }

    override suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: TaskInterval
    ): Result<TaskInterval, DataError.Remote> {
        intervalRoutes += "$projectId/$taskId"
        intervalUpdateFailWith?.let { return Result.Error(it) }
        updatedIntervalIds += interval.intervalId
        return Result.Success(interval)
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        intervalRoutes += "$projectId/$taskId"
        intervalDeleteFailWith?.let { return Result.Error(it) }
        deletedIntervalIds += intervalId
        return Result.Success(Unit)
    }
}

/**
 * In-memory pending-sync queue. Reimplements the DAO's dedup rules rather than delegating, because
 * those rules (a pending CREATE absorbs later UPDATEs; a DELETE cancels a pending CREATE) are part
 * of the behaviour under test.
 */
private class FakePendingSyncDataSource : PendingSyncDataSource {
    private val ops = mutableListOf<PendingSyncOperation>()

    fun all(): List<PendingSyncOperation> = ops.sortedBy { it.createdAt }

    override suspend fun getPendingOperations(): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(all())

    override suspend fun getOperationsByEntityId(
        entityId: String
    ): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(ops.filter { it.entityId == entityId }.sortedBy { it.createdAt })

    override suspend fun hasPendingCreate(entityId: String): Result<Boolean, DataError.Local> =
        Result.Success(ops.any { it.entityId == entityId && it.operationType == PendingSyncOperation.OP_CREATE })

    override suspend fun enqueue(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?,
        createdAt: Instant
    ): EmptyResult<DataError.Local> {
        val existing = ops.filter { it.entityId == entityId }
        val op = PendingSyncOperation(
            operationId = Uuid.random().toString(),
            entityId = entityId,
            entityType = entityType,
            operationType = operationType,
            createdAt = createdAt,
            parentEntityId = parentEntityId
        )
        when (operationType) {
            PendingSyncOperation.OP_CREATE -> ops += op
            PendingSyncOperation.OP_UPDATE -> {
                val alreadyQueued = existing.any {
                    it.operationType == PendingSyncOperation.OP_CREATE ||
                        it.operationType == PendingSyncOperation.OP_UPDATE
                }
                if (!alreadyQueued) ops += op
            }
            PendingSyncOperation.OP_DELETE -> {
                val hadPendingCreate = existing.any { it.operationType == PendingSyncOperation.OP_CREATE }
                ops.removeAll { it.entityId == entityId }
                if (!hadPendingCreate) ops += op
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteOperation(operationId: String): EmptyResult<DataError.Local> {
        ops.removeAll { it.operationId == operationId }
        return Result.Success(Unit)
    }

    override suspend fun deleteOperationsByEntityId(entityId: String): EmptyResult<DataError.Local> {
        ops.removeAll { it.entityId == entityId }
        return Result.Success(Unit)
    }
}

private class FakeSyncScheduler : SyncScheduler {
    var scheduleCount = 0
    override suspend fun schedulePeriodicSync() { scheduleCount++ }
    override suspend fun schedulePeriodicSyncOnStart() = Unit
    override suspend fun cancelAllSyncs() = Unit
}
