@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The ordering contract: a whole offline session — project, task and timer — has to reach the
 * server in dependency order once connectivity comes back, because each child's route is nested
 * inside its parent's.
 */
internal class SyncCoordinatorTest {

    private fun task(taskId: String, projectId: String) = ProjectTask(
        projectTaskId = taskId,
        title = "task-$taskId",
        durationMillis = 0,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = projectId,
        isTimerRunning = false,
    )

    /** Creates a project, a task under it and one tracked interval, all with the network down. */
    private suspend fun RepoFixture.recordAnOfflineSession() {
        remoteProject.failWith = DataError.Remote.NO_INTERNET
        remoteTask.failWith = DataError.Remote.NO_INTERNET
        remoteInterval.postFailWith = DataError.Remote.NO_INTERNET

        projectRepository.upsertProject(db.newProject("p1"))
        taskRepository.upsertProjectTask(task("t1", "p1"))
        taskRepository.startProjectTask("t1")
        localTask.clock = Instant.fromEpochMilliseconds(70_000)
        taskRepository.stopProjectTask("t1")
    }

    private fun RepoFixture.goOnline() {
        remoteProject.failWith = null
        remoteTask.failWith = null
        remoteInterval.postFailWith = null
        remoteInterval.updateFailWith = null
    }

    @Test
    fun syncPendingOperations_pushesTheWholeOfflineSession_parentsBeforeChildren() = runBlocking<Unit> {
        val f = RepoFixture()
        f.recordAnOfflineSession()
        f.goOnline()

        f.syncCoordinator.syncPendingOperations()

        assertEquals(listOf("p1"), f.remoteProject.postedProjectIds)
        assertEquals(listOf("t1"), f.remoteTask.postedTaskIds)
        assertEquals(listOf("i1"), f.remoteInterval.postedIntervalIds)
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun syncPendingOperations_holdsBackTasksAndIntervals_whileTheProjectPushKeepsFailing() = runBlocking<Unit> {
        val f = RepoFixture()
        f.recordAnOfflineSession()
        // Tasks and intervals could go through now, but their project still cannot.
        f.remoteTask.failWith = null
        f.remoteInterval.postFailWith = null

        f.syncCoordinator.syncPendingOperations()

        assertTrue(f.remoteTask.postedTaskIds.isEmpty())
        assertTrue(f.remoteInterval.postedIntervalIds.isEmpty())
        // Nothing was dropped — all three are still queued for the next attempt.
        assertEquals(3, f.queue.all().size)
    }

    @Test
    fun syncPendingOperations_holdsBackTheInterval_whileItsTaskPushKeepsFailing() = runBlocking<Unit> {
        val f = RepoFixture()
        f.recordAnOfflineSession()
        f.goOnline()
        f.remoteTask.failWith = DataError.Remote.NO_INTERNET // only the task route is still down

        f.syncCoordinator.syncPendingOperations()

        assertEquals(listOf("p1"), f.remoteProject.postedProjectIds)
        assertTrue(f.remoteInterval.postedIntervalIds.isEmpty())
        // Project drained; task and interval remain.
        assertEquals(2, f.queue.all().size)
    }

    @Test
    fun syncPendingOperations_recoversOnASecondPass_onceTheProjectLands() = runBlocking<Unit> {
        val f = RepoFixture()
        f.recordAnOfflineSession()
        f.remoteTask.failWith = null
        f.remoteInterval.postFailWith = null

        f.syncCoordinator.syncPendingOperations() // project still down: nothing drains
        f.goOnline()
        f.syncCoordinator.syncPendingOperations()

        assertEquals(listOf("p1"), f.remoteProject.postedProjectIds)
        assertEquals(listOf("t1"), f.remoteTask.postedTaskIds)
        assertEquals(listOf("i1"), f.remoteInterval.postedIntervalIds)
        assertTrue(f.queue.all().isEmpty())
    }
}
