@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Intervals are only ever created through the timer, so these drive the repository the way the app
 * does — `startTask` / `stopTask`, which hand the interval to this repository to push.
 */
internal class OfflineFirstIntervalRepositoryTest {

    /** One task "t1" under project "p1", timer stopped, both already known to the server. */
    private fun fixture() = RepoFixture().apply { seedProjectWithTask() }

    @Test
    fun startTask_postsTheNewIntervalToTheTasksRoute() = runBlocking<Unit> {
        val f = fixture()

        f.projectRepository.startTask("t1")

        assertEquals(listOf("i1"), f.remoteInterval.postedIntervalIds)
        // The route is built from the interval's own parentProjectId — no task lookup involved.
        assertEquals(listOf("p1/t1"), f.remoteInterval.intervalRoutes)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun stopTask_putsTheClosedInterval() = runBlocking<Unit> {
        val f = fixture()

        f.projectRepository.startTask("t1")
        f.localProject.clock = Instant.fromEpochMilliseconds(70_000) // 60s after the default start
        f.projectRepository.stopTask("t1")

        assertEquals(listOf("i1"), f.remoteInterval.updatedIntervalIds)
        assertEquals(60_000L, f.db.intervals.getValue("i1").durationMillis)
    }

    @Test
    fun startTask_queuesTheInterval_whenOffline() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1")

        // Local write stands regardless — the user keeps tracking time.
        assertNotNull(f.db.intervals["i1"])

        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
        assertEquals("i1", ops[0].entityId)
        // parentEntityId carries the TASK id for intervals; a queued DELETE has no local row left
        // to read parentProjectId from, so it resolves the project through the task at drain time.
        assertEquals("t1", ops[0].parentEntityId)
        assertTrue(f.scheduler.scheduleCount > 0)
    }

    /**
     * The gate: the task was created offline, so there is no `/tasks/{taskId}` to hang the interval
     * off. The interval is queued without a request ever being sent.
     */
    @Test
    fun startTask_queuesTheInterval_withoutCallingTheServer_whenTheTaskIsStillPendingCreate() = runBlocking<Unit> {
        val f = RepoFixture()
        f.db.seedProject("p1")
        f.remoteProject.failWith = DataError.Remote.NO_INTERNET
        f.projectRepository.upsertProjectTask(f.db.newTask("t1", "p1")) // task CREATE queued
        f.remoteProject.failWith = null
        f.remoteInterval.intervalRoutes.clear()

        f.projectRepository.startTask("t1")

        // No request went out at all — the parent-pending check short-circuits before the network.
        assertTrue(f.remoteInterval.intervalRoutes.isEmpty())
        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
    }

    /**
     * The backstop for the same situation when the queue row is missing: the server answers 404,
     * and queueing (rather than dropping) is what lets the ordered drain push the task first and
     * the interval after.
     */
    @Test
    fun startTask_queuesTheInterval_whenTheServerSaysTheTaskIsNotThere() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NOT_FOUND

        f.projectRepository.startTask("t1")

        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
    }

    /** A 409 means the POST already landed and only its response was lost. */
    @Test
    fun startTask_retriesAsUpdate_whenTheServerReportsADuplicate() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.CONFLICT

        f.projectRepository.startTask("t1")

        assertEquals(listOf("i1"), f.remoteInterval.updatedIntervalIds)
        assertTrue(f.remoteInterval.postedIntervalIds.isEmpty())
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun syncPendingIntervals_pushesQueuedInterval_andClearsQueue() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1") // queued while offline
        f.remoteInterval.postFailWith = null    // back online

        f.intervalRepository.syncPendingIntervals()

        assertEquals(listOf("i1"), f.remoteInterval.postedIntervalIds)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun syncPendingIntervals_dropsQueuedInterval_whenItWasDeletedLocally() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1")
        f.db.intervals.remove("i1")          // gone before the queue drained
        f.remoteInterval.postFailWith = null

        f.intervalRepository.syncPendingIntervals()

        assertTrue(f.remoteInterval.postedIntervalIds.isEmpty())
        // Dropped, not retried forever.
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun syncPendingIntervals_dropsQueuedInterval_whenItsTaskIsGone() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1")
        // Deleting the task cascades to its intervals, so the queued op has nothing left to push.
        f.localProject.deleteProjectTask("t1")
        f.remoteInterval.postFailWith = null

        f.intervalRepository.syncPendingIntervals()

        assertTrue(f.remoteInterval.postedIntervalIds.isEmpty())
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun syncPendingIntervals_retriesQueuedInterval_whenStillOffline() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1")
        f.intervalRepository.syncPendingIntervals() // still offline

        // Left queued for the next attempt.
        assertEquals(1, f.queue.all().count { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun deleteTaskInterval_deletesLocallyAndRemotely() = runBlocking<Unit> {
        val f = fixture()
        f.projectRepository.startTask("t1")

        f.intervalRepository.deleteTaskInterval("i1")

        assertNull(f.db.intervals["i1"])
        assertEquals(listOf("i1"), f.remoteInterval.deletedIntervalIds)
    }

    @Test
    fun deleteTaskInterval_dropsThePendingCreate_andSkipsTheServer() = runBlocking<Unit> {
        val f = fixture()
        f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        f.projectRepository.startTask("t1")    // create is queued, never reached the server
        f.intervalRepository.deleteTaskInterval("i1")

        // Nothing to delete server-side — the interval never got there.
        assertTrue(f.remoteInterval.deletedIntervalIds.isEmpty())
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun deleteTaskInterval_queuesTheDelete_whenOffline() = runBlocking<Unit> {
        val f = fixture()
        f.projectRepository.startTask("t1")                       // succeeds online
        f.remoteInterval.deleteFailWith = DataError.Remote.NO_INTERNET

        f.intervalRepository.deleteTaskInterval("i1")

        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_DELETE, ops[0].operationType)
        assertEquals("t1", ops[0].parentEntityId)
    }

    @Test
    fun syncPendingIntervals_pushesQueuedIntervalDelete() = runBlocking<Unit> {
        val f = fixture()
        f.projectRepository.startTask("t1")
        f.remoteInterval.deleteFailWith = DataError.Remote.NO_INTERNET
        f.intervalRepository.deleteTaskInterval("i1")

        f.remoteInterval.deleteFailWith = null
        f.intervalRepository.syncPendingIntervals()

        // The interval row is gone locally, so the delete has to survive on the queued task id alone.
        assertEquals(listOf("i1"), f.remoteInterval.deletedIntervalIds)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun upsertTaskInterval_updatesAnExistingIntervalRatherThanCreatingIt() = runBlocking<Unit> {
        val f = fixture()
        f.projectRepository.startTask("t1")

        f.intervalRepository.updateTaskInterval(
            f.db.intervals.getValue("i1").copy(durationMillis = 5_000)
        )

        assertEquals(listOf("i1"), f.remoteInterval.updatedIntervalIds)
        // Only the original startTask create.
        assertEquals(listOf("i1"), f.remoteInterval.postedIntervalIds)
    }
}
