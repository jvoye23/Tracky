@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class OfflineFirstTaskRepositoryTest {

    private fun task(taskId: String, projectId: String = "p1") = ProjectTask(
        projectTaskId = taskId,
        title = "task-$taskId",
        description = null,
        durationMillis = 0,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = projectId,
        isTimerRunning = false,
    )

    /** Project "p1" already on the server, no tasks yet. */
    private fun fixture(time: FakeTimeProvider = FakeTimeProvider()) =
        RepoFixture(time).apply { db.seedProject("p1") }

    @Test
    fun upsertProjectTask_postsToTheProjectsTasksRoute() = runBlocking<Unit> {
        val f = fixture()

        val result = f.taskRepository.upsertProjectTask(task("t1"))

        assertTrue(result is Result.Success)
        assertEquals(listOf("t1"), f.remoteTask.postedTaskIds)
        assertEquals(listOf("p1/t1"), f.remoteTask.taskRoutes)
        assertNotNull(f.db.tasks["t1"])
    }

    @Test
    fun upsertProjectTask_updatesAnExistingTaskRatherThanCreatingIt() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))

        f.taskRepository.upsertProjectTask(f.db.tasks.getValue("t1").copy(title = "renamed"))

        assertEquals(listOf("t1"), f.remoteTask.updatedTaskIds)
        assertEquals(listOf("t1"), f.remoteTask.postedTaskIds) // only the first call created
    }

    @Test
    fun upsertProjectTask_queuesCreate_whenRemoteOffline() = runBlocking<Unit> {
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET

        val result = f.taskRepository.upsertProjectTask(task("t1"))

        // User sees success because the local write succeeded.
        assertTrue(result is Result.Success)
        assertNotNull(f.db.tasks["t1"])

        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
        assertEquals("t1", ops[0].entityId)
        assertTrue(f.scheduler.scheduleCount > 0)
    }

    @Test
    fun upsertProjectTask_stampsUpdatedAt_fromTheInjectedClock() = runBlocking<Unit> {
        val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(1_234_567))
        val f = fixture(time)

        f.taskRepository.upsertProjectTask(task("t1"))

        assertEquals(time.now, f.db.tasks.getValue("t1").ownUpdatedAt)
    }

    /**
     * The gate this refactor exists for: a task whose project was created offline has no
     * `/api/projects/{projectId}` to hang off, so it must not spend a request to find that out.
     */
    @Test
    fun upsertProjectTask_queuesWithoutCallingTheServer_whenTheProjectIsStillPendingCreate() = runBlocking<Unit> {
        val f = RepoFixture()
        f.remoteProject.failWith = DataError.Remote.NO_INTERNET
        f.projectRepository.upsertProject(f.db.newProject("p1")) // project CREATE queued
        f.remoteProject.failWith = null

        f.taskRepository.upsertProjectTask(task("t1"))

        assertTrue(f.remoteTask.taskRoutes.isEmpty())
        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_CREATE, ops[0].operationType)
    }

    @Test
    fun syncPendingTasks_pushesQueuedCreate_andClearsQueue() = runBlocking<Unit> {
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.upsertProjectTask(task("t1")) // queued while offline
        f.remoteTask.failWith = null

        f.taskRepository.syncPendingTasks()

        assertEquals(listOf("t1"), f.remoteTask.postedTaskIds)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    /** The drain-side half of the gate: a doomed task push is postponed, not attempted and dropped. */
    @Test
    fun syncPendingTasks_leavesTheTaskQueued_whileItsProjectIsStillPendingCreate() = runBlocking<Unit> {
        val f = RepoFixture()
        f.remoteProject.failWith = DataError.Remote.NO_INTERNET
        f.projectRepository.upsertProject(f.db.newProject("p1"))
        f.taskRepository.upsertProjectTask(task("t1"))
        // Tasks can reach the server again, but the project still cannot.
        f.remoteTask.failWith = null

        f.taskRepository.syncPendingTasks()

        assertTrue(f.remoteTask.postedTaskIds.isEmpty())
        assertEquals(1, f.queue.all().count { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    @Test
    fun syncPendingTasks_dropsQueuedTask_whenItWasDeletedLocally() = runBlocking<Unit> {
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.upsertProjectTask(task("t1"))
        f.db.tasks.remove("t1")     // gone before the queue drained
        f.remoteTask.failWith = null

        f.taskRepository.syncPendingTasks()

        assertTrue(f.remoteTask.postedTaskIds.isEmpty())
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    @Test
    fun syncPendingTasks_retriesQueuedTask_whenStillOffline() = runBlocking<Unit> {
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.upsertProjectTask(task("t1"))

        f.taskRepository.syncPendingTasks() // still offline

        assertEquals(1, f.queue.all().count { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    @Test
    fun deleteProjectTask_deletesLocallyAndRemotely() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))

        val result = f.taskRepository.deleteProjectTask("p1", "t1")

        assertTrue(result is Result.Success)
        assertNull(f.db.tasks["t1"])
        assertEquals(listOf("t1"), f.remoteTask.deletedTaskIds)
    }

    @Test
    fun deleteProjectTask_droppedLocally_whenStillPendingCreate_neverHitsServer() = runBlocking<Unit> {
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.upsertProjectTask(task("t1")) // queued CREATE, never reached the server
        f.remoteTask.failWith = null

        f.taskRepository.deleteProjectTask("p1", "t1")

        assertNull(f.db.tasks["t1"])
        assertFalse(f.remoteTask.deletedTaskIds.contains("t1"))
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    /** The project never reached the server either, so its cascade will take the task with it. */
    @Test
    fun deleteProjectTask_skipsTheServer_whenTheProjectIsStillPendingCreate() = runBlocking<Unit> {
        val f = RepoFixture()
        f.remoteProject.failWith = DataError.Remote.NO_INTERNET
        f.projectRepository.upsertProject(f.db.newProject("p1"))
        f.db.seedTask("t1", "p1")   // seeded directly: never queued, so no ghost-create to drop

        f.taskRepository.deleteProjectTask("p1", "t1")

        assertNull(f.db.tasks["t1"])
        assertTrue(f.remoteTask.deletedTaskIds.isEmpty())
    }

    @Test
    fun deleteProjectTask_queuesTheDelete_whenOffline() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))    // succeeds online
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET

        f.taskRepository.deleteProjectTask("p1", "t1")

        val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK }
        assertEquals(1, ops.size)
        assertEquals(PendingSyncOperation.OP_DELETE, ops[0].operationType)
        // The task row is gone by drain time, so the route has to survive on the queued project id.
        assertEquals("p1", ops[0].parentEntityId)
    }

    @Test
    fun syncPendingTasks_pushesQueuedTaskDelete() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.deleteProjectTask("p1", "t1")

        f.remoteTask.failWith = null
        f.taskRepository.syncPendingTasks()

        assertEquals(listOf("t1"), f.remoteTask.deletedTaskIds)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    /** A 409 means the POST already landed; last-write-wins then decides on `ownUpdatedAt`. */
    @Test
    fun upsertProjectTask_onConflict_pushesLocal_whenLocalIsNewer() = runBlocking<Unit> {
        val f = fixture(FakeTimeProvider(now = Instant.fromEpochMilliseconds(500)))
        f.remoteTask.tasksToReturn = listOf(
            task("t1").copy(ownUpdatedAt = Instant.fromEpochMilliseconds(100))
        )
        f.remoteTask.postFailWith = DataError.Remote.CONFLICT

        f.taskRepository.upsertProjectTask(task("t1"))

        // Local stamp (500) beats the server's (100), so the local row is pushed as an update
        // rather than being overwritten by the older server copy.
        assertEquals(listOf("t1"), f.remoteTask.updatedTaskIds)
        assertTrue(f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK })
    }

    @Test
    fun upsertProjectTask_onConflict_takesTheServerCopy_whenTheServerIsNewer() = runBlocking<Unit> {
        val f = fixture(FakeTimeProvider(now = Instant.fromEpochMilliseconds(100)))
        f.remoteTask.tasksToReturn = listOf(
            task("t1").copy(title = "renamed elsewhere", ownUpdatedAt = Instant.fromEpochMilliseconds(500))
        )
        f.remoteTask.postFailWith = DataError.Remote.CONFLICT

        f.taskRepository.upsertProjectTask(task("t1"))

        assertEquals("renamed elsewhere", f.db.tasks.getValue("t1").title)
        assertTrue(f.remoteTask.updatedTaskIds.isEmpty())
    }

    @Test
    fun updateProjectTaskTitle_routesThroughTheOfflineFirstUpsert() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))
        f.remoteTask.updatedTaskIds.clear()

        f.taskRepository.updateProjectTaskTitle("t1", "renamed")

        assertEquals("renamed", f.db.tasks.getValue("t1").title)
        // A title edit is not local-only: it has to reach the server like any other write.
        assertEquals(listOf("t1"), f.remoteTask.updatedTaskIds)
    }

    @Test
    fun updateProjectTaskText_keepsTheStoredRowAndReachesTheServer() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1").copy(sortIndex = 4))
        f.remoteTask.updatedTaskIds.clear()

        f.taskRepository.updateProjectTaskText("t1", "renamed", "described")

        val stored = f.db.tasks.getValue("t1")
        assertEquals("renamed", stored.title)
        assertEquals("described", stored.description)
        // Started from the stored row, so the task keeps its place in the manual order.
        assertEquals(4L, stored.sortIndex)
        assertEquals(listOf("t1"), f.remoteTask.updatedTaskIds)
    }

    @Test
    fun stopProjectTask_pushesTheIntervalAndTheTask() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))
        f.taskRepository.startProjectTask("t1")
        f.localTask.clock = Instant.fromEpochMilliseconds(70_000)
        f.remoteTask.updatedTaskIds.clear()

        f.taskRepository.stopProjectTask("t1")

        // The interval's close now goes through the timer resource — a compare-and-swap naming the
        // interval — rather than a blind PUT to the interval route. The task still carries the
        // accumulated total and the cleared flag, and still goes the ordinary way.
        val (intervalId, kind, endedAt) = f.activeTimer.stops.single()
        assertEquals("i1", intervalId)
        assertEquals(ActiveTimerKind.TASK, kind)
        // The instant the interval was actually closed at, not "now" read a second time.
        assertEquals(Instant.fromEpochMilliseconds(70_000), endedAt)
        assertEquals(listOf("t1"), f.remoteTask.updatedTaskIds)
        assertFalse(f.db.tasks.getValue("t1").isTimerRunning)
    }

    @Test
    fun startProjectTask_announcesTheOpenedIntervalToTheServer() = runBlocking<Unit> {
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))

        f.taskRepository.startProjectTask("t1")

        // The server closes whatever was running on the user's other devices at this start.
        val (taskInterval, subTaskInterval) = f.activeTimer.starts.single()
        assertEquals("i1", taskInterval.intervalId)
        assertNull(subTaskInterval)
    }

    @Test
    fun startProjectTask_skipsTheServer_whenTheTaskItselfIsStillQueuedForCreation() = runBlocking<Unit> {
        // A task that exists only here has no server-side row for the timer resource to hang off,
        // so the start goes on the interval queue instead of spending a doomed request.
        val f = fixture()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET
        f.taskRepository.upsertProjectTask(task("t1"))

        f.taskRepository.startProjectTask("t1")

        assertTrue(f.activeTimer.starts.isEmpty())
        assertTrue(f.queue.all().any { it.entityType == PendingSyncOperation.ENTITY_INTERVAL })
    }

    @Test
    fun stopProjectTask_neverBanksTheDuration_whenAnotherDeviceStartedTheTimer() = runBlocking<Unit> {
        // The sharpest double-count risk in the feature. stopTask is what calls addTaskDuration,
        // and the server's task row already carries a foreign timer's time — running it here too
        // would charge the user twice for the same minutes.
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))
        f.taskRepository.startProjectTask("t1")
        f.db.intervals["i1"] = f.db.intervals.getValue("i1")
            .copy(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)
        val bankedBefore = f.db.tasks.getValue("t1").durationMillis
        f.localTask.clock = Instant.fromEpochMilliseconds(70_000)

        f.taskRepository.stopProjectTask("t1")

        assertEquals(bankedBefore, f.db.tasks.getValue("t1").durationMillis)
        // The local row is left open: the echo closes it, at the instant the server recorded.
        assertNull(f.db.intervals.getValue("i1").endDateTimeUtc)
        // And the stop still reached the server, which is the whole point of the feature.
        assertEquals("i1", f.activeTimer.stops.single().first)
    }

    @Test
    fun stopProjectTask_banksNormally_whenThisDeviceStartedTheTimer() = runBlocking<Unit> {
        // The other half of the guard: a null device id means "this device", so the ordinary
        // local close and bank must still happen for every row written before the column existed.
        val f = fixture()
        f.taskRepository.upsertProjectTask(task("t1"))
        f.taskRepository.startProjectTask("t1")
        f.db.intervals["i1"] = f.db.intervals.getValue("i1").copy(startedByDeviceId = null)
        f.localTask.clock = Instant.fromEpochMilliseconds(70_000)

        f.taskRepository.stopProjectTask("t1")

        assertEquals(60_000, f.db.tasks.getValue("t1").durationMillis)
        assertNotNull(f.db.intervals.getValue("i1").endDateTimeUtc)
    }
}
