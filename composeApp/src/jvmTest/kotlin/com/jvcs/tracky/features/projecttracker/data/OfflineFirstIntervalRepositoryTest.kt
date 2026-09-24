@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.projecttracker.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Intervals are only ever created through the timer, so these drive the repository the way the app
 * does — `startProjectTask` / `stopProjectTask` on the task repository, which hands the interval to
 * this one.
 */
internal class OfflineFirstIntervalRepositoryTest {

    /** One task "t1" under project "p1", timer stopped, both already known to the server. */
    private fun fixture() = RepoFixture().apply { seedProjectWithTask() }

    @Test
    fun startTask_postsTheNewIntervalToTheTasksRoute() =
        runBlocking<Unit> {
            val f = fixture()

            f.createIntervalForTask()

            assertThat(f.remoteInterval.postedIntervalIds).isEqualTo(listOf("i1"))
            // The route is built from the interval's own parentProjectId — no task lookup involved.
            assertThat(f.remoteInterval.intervalRoutes).isEqualTo(listOf("p1/t1"))
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun stopTask_putsTheClosedInterval() =
        runBlocking<Unit> {
            val f = fixture()

            f.createIntervalForTask()
            f.localTask.clock = Instant.fromEpochMilliseconds(70_000) // 60s after the default start
            f.closeIntervalForTask()

            assertThat(f.remoteInterval.updatedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(
                f.db.intervals
                    .getValue("i1")
                    .durationMillis,
            ).isEqualTo(60_000L)
        }

    @Test
    fun startTask_queuesTheInterval_whenOffline() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask()

            // Local write stands regardless — the user keeps tracking time.
            assertThat(f.db.intervals["i1"]).isNotNull()

            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
            assertThat(ops[0].entityId).isEqualTo("i1")
            // parentEntityId carries the TASK id for intervals; a queued DELETE has no local row left
            // to read parentProjectId from, so it resolves the project through the task at drain time.
            assertThat(ops[0].parentEntityId).isEqualTo("t1")
            assertThat(f.scheduler.scheduleCount > 0).isTrue()
        }

    /**
     * The gate: the task was created offline, so there is no `/tasks/{taskId}` to hang the interval
     * off. The interval is queued without a request ever being sent.
     */
    @Test
    fun startTask_queuesTheInterval_withoutCallingTheServer_whenTheTaskIsStillPendingCreate() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.db.seedProject("p1")
            f.remoteTask.failWith = DataError.Remote.NO_INTERNET
            f.taskRepository.upsertProjectTask(f.db.newTask("t1", "p1")) // task CREATE queued
            f.remoteTask.failWith = null
            f.remoteInterval.intervalRoutes.clear()

            f.createIntervalForTask()

            // No request went out at all — the parent-pending check short-circuits before the network.
            assertThat(f.remoteInterval.intervalRoutes.isEmpty()).isTrue()
            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
        }

    /**
     * The backstop for the same situation when the queue row is missing: the server answers 404,
     * and queueing (rather than dropping) is what lets the ordered drain push the task first and
     * the interval after.
     */
    @Test
    fun startTask_queuesTheInterval_whenTheServerSaysTheTaskIsNotThere() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NOT_FOUND

            f.createIntervalForTask()

            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
        }

    /** A 409 means the POST already landed and only its response was lost. */
    @Test
    fun startTask_retriesAsUpdate_whenTheServerReportsADuplicate() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.CONFLICT

            f.createIntervalForTask()

            assertThat(f.remoteInterval.updatedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(f.remoteInterval.postedIntervalIds.isEmpty()).isTrue()
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun syncPendingIntervals_pushesQueuedInterval_andClearsQueue() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask() // queued while offline
            f.remoteInterval.postFailWith = null // back online

            f.intervalRepository.syncPendingIntervals()

            assertThat(f.remoteInterval.postedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun syncPendingIntervals_dropsQueuedInterval_whenItWasDeletedLocally() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask()
            f.db.intervals.remove("i1") // gone before the queue drained
            f.remoteInterval.postFailWith = null

            f.intervalRepository.syncPendingIntervals()

            assertThat(f.remoteInterval.postedIntervalIds.isEmpty()).isTrue()
            // Dropped, not retried forever.
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun syncPendingIntervals_dropsQueuedInterval_whenItsTaskIsGone() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask()
            // Deleting the task cascades to its intervals, so the queued op has nothing left to push.
            f.localTask.deleteProjectTask("t1")
            f.remoteInterval.postFailWith = null

            f.intervalRepository.syncPendingIntervals()

            assertThat(f.remoteInterval.postedIntervalIds.isEmpty()).isTrue()
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun syncPendingIntervals_retriesQueuedInterval_whenStillOffline() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask()
            f.intervalRepository.syncPendingIntervals() // still offline

            // Left queued for the next attempt.
            assertThat(f.queue.all().count { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isEqualTo(1)
        }

    @Test
    fun deleteTaskInterval_deletesLocallyAndRemotely() =
        runBlocking<Unit> {
            val f = fixture()
            f.createIntervalForTask()

            f.intervalRepository.deleteTaskInterval("i1")

            assertThat(f.db.intervals["i1"]).isNull()
            assertThat(f.remoteInterval.deletedIntervalIds).isEqualTo(listOf("i1"))
        }

    @Test
    fun deleteTaskInterval_dropsThePendingCreate_andSkipsTheServer() =
        runBlocking<Unit> {
            val f = fixture()
            f.remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

            f.createIntervalForTask() // create is queued, never reached the server
            f.intervalRepository.deleteTaskInterval("i1")

            // Nothing to delete server-side — the interval never got there.
            assertThat(f.remoteInterval.deletedIntervalIds.isEmpty()).isTrue()
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun deleteTaskInterval_queuesTheDelete_whenOffline() =
        runBlocking<Unit> {
            val f = fixture()
            f.createIntervalForTask() // succeeds online
            f.remoteInterval.deleteFailWith = DataError.Remote.NO_INTERNET

            f.intervalRepository.deleteTaskInterval("i1")

            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_DELETE)
            assertThat(ops[0].parentEntityId).isEqualTo("t1")
        }

    @Test
    fun syncPendingIntervals_pushesQueuedIntervalDelete() =
        runBlocking<Unit> {
            val f = fixture()
            f.createIntervalForTask()
            f.remoteInterval.deleteFailWith = DataError.Remote.NO_INTERNET
            f.intervalRepository.deleteTaskInterval("i1")

            f.remoteInterval.deleteFailWith = null
            f.intervalRepository.syncPendingIntervals()

            // The interval row is gone locally, so the delete has to survive on the queued task id alone.
            assertThat(f.remoteInterval.deletedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_INTERVAL }).isTrue()
        }

    @Test
    fun upsertTaskInterval_updatesAnExistingIntervalRatherThanCreatingIt() =
        runBlocking<Unit> {
            val f = fixture()
            f.createIntervalForTask()

            f.intervalRepository.updateTaskInterval(
                f.db.intervals
                    .getValue("i1")
                    .copy(durationMillis = 5_000),
            )

            assertThat(f.remoteInterval.updatedIntervalIds).isEqualTo(listOf("i1"))
            // Only the original startProjectTask create.
            assertThat(f.remoteInterval.postedIntervalIds).isEqualTo(listOf("i1"))
        }
}
