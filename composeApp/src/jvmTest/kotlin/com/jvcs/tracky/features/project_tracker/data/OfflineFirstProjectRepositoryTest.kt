@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

        repository.syncPendingProjects()

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

        repository.syncPendingProjects()

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

    private data class Quad(
        val local: FakeLocalProjectDataSource,
        val remote: FakeRemoteProjectDataSource,
        val queue: FakePendingSyncDataSource,
        val scheduler: FakeSyncScheduler
    )

    private fun Quad.repository() = repo(local, remote, queue, scheduler)

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
}
