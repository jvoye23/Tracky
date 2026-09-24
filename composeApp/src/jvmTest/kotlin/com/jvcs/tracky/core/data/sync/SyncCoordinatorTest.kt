@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.core.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.projecttracker.data.RepoFixture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The ordering contract: a whole offline session — project, task, timer, subtask and the subtask's
 * own tracked time — has to reach the server in dependency order once connectivity comes back,
 * because each child's route is nested inside its parent's.
 */
internal class SyncCoordinatorTest {

    private fun task(taskId: String, projectId: String) =
        ProjectTask(
            projectTaskId = taskId,
            title = "task-$taskId",
            description = null,
            durationMillis = 0,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            parentProjectId = projectId,
            isTimerRunning = false,
        )

    /**
     * Creates a project, a task under it, one tracked interval, one subtask and one subtask
     * interval — the full four-level tree — all with the network down.
     */
    private suspend fun RepoFixture.recordAnOfflineSession() {
        remoteProject.failWith = DataError.Remote.NO_INTERNET
        remoteTask.failWith = DataError.Remote.NO_INTERNET
        remoteInterval.postFailWith = DataError.Remote.NO_INTERNET
        remoteSubTask.postFailWith = DataError.Remote.NO_INTERNET
        remoteSubTaskInterval.postFailWith = DataError.Remote.NO_INTERNET

        projectRepository.upsertProject(db.newProject("p1"))
        taskRepository.upsertProjectTask(task("t1", "p1"))
        taskRepository.startProjectTask("t1")
        localTask.clock = Instant.fromEpochMilliseconds(70_000)
        taskRepository.stopProjectTask("t1")
        subTaskRepository.upsertSubTask(db.newSubTask("s1", "t1", "p1"))
        subTaskIntervalRepository.createSubTaskInterval(db.newSubTaskInterval("si1", "s1", "i1", "p1"))
    }

    private fun RepoFixture.goOnline() {
        remoteProject.failWith = null
        remoteTask.failWith = null
        remoteInterval.postFailWith = null
        remoteInterval.updateFailWith = null
        remoteSubTask.postFailWith = null
        remoteSubTaskInterval.postFailWith = null
    }

    @Test
    fun syncPendingOperations_pushesTheWholeOfflineSession_parentsBeforeChildren() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.recordAnOfflineSession()
            f.goOnline()

            f.syncCoordinator.syncPendingOperations()

            assertThat(f.remoteProject.postedProjectIds).isEqualTo(listOf("p1"))
            assertThat(f.remoteTask.postedTaskIds).isEqualTo(listOf("t1"))
            assertThat(f.remoteInterval.postedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(f.remoteSubTask.postedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(f.remoteSubTaskInterval.postedIntervalIds).isEqualTo(listOf("si1"))
            assertThat(f.queue.all().isEmpty()).isTrue()
        }

    @Test
    fun syncPendingOperations_reportsAnUnreadableQueue_andLeavesItForTheNextRun() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.recordAnOfflineSession()
            f.goOnline()
            f.queue.failQueueReads = true

            val unreadable = f.syncCoordinator.syncPendingOperations()

            // Not mistaken for an empty queue: the caller hears about it, and nothing was pushed
            // or dropped, so the next run still has the whole session to send.
            assertThat(unreadable).isEqualTo(Result.Error(DataError.Local.UNKNOWN))
            assertThat(f.remoteProject.postedProjectIds).isEqualTo(emptyList())
            assertThat(f.queue.all().size).isEqualTo(5)

            f.queue.failQueueReads = false
            val drained = f.syncCoordinator.syncPendingOperations()

            assertThat(drained).isEqualTo(Result.Success(Unit))
            assertThat(f.queue.all().isEmpty()).isEqualTo(true)
        }

    @Test
    fun syncPendingOperations_holdsBackTasksAndIntervals_whileTheProjectPushKeepsFailing() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.recordAnOfflineSession()
            // Tasks and intervals could go through now, but their project still cannot.
            f.remoteTask.failWith = null
            f.remoteInterval.postFailWith = null
            f.remoteSubTask.postFailWith = null
            f.remoteSubTaskInterval.postFailWith = null

            f.syncCoordinator.syncPendingOperations()

            assertThat(f.remoteTask.postedTaskIds.isEmpty()).isTrue()
            assertThat(f.remoteInterval.postedIntervalIds.isEmpty()).isTrue()
            // A subtask is two levels below the project, so it is held back just as far.
            assertThat(f.remoteSubTask.postedSubTaskIds.isEmpty()).isTrue()
            // Three levels below the project, and held back just as far.
            assertThat(f.remoteSubTaskInterval.postedIntervalIds.isEmpty()).isTrue()
            // Nothing was dropped — all five are still queued for the next attempt.
            assertThat(f.queue.all().size).isEqualTo(5)
        }

    @Test
    fun syncPendingOperations_holdsBackTheInterval_whileItsTaskPushKeepsFailing() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.recordAnOfflineSession()
            f.goOnline()
            f.remoteTask.failWith = DataError.Remote.NO_INTERNET // only the task route is still down

            f.syncCoordinator.syncPendingOperations()

            assertThat(f.remoteProject.postedProjectIds).isEqualTo(listOf("p1"))
            assertThat(f.remoteInterval.postedIntervalIds.isEmpty()).isTrue()
            // The subtask hangs off the same failing task.
            assertThat(f.remoteSubTask.postedSubTaskIds.isEmpty()).isTrue()
            assertThat(f.remoteSubTaskInterval.postedIntervalIds.isEmpty()).isTrue()
            // Project drained; task, interval, subtask and subtask interval remain.
            assertThat(f.queue.all().size).isEqualTo(4)
        }

    @Test
    fun syncPendingOperations_recoversOnASecondPass_onceTheProjectLands() =
        runBlocking<Unit> {
            val f = RepoFixture()
            f.recordAnOfflineSession()
            f.remoteTask.failWith = null
            f.remoteInterval.postFailWith = null
            f.remoteSubTask.postFailWith = null
            f.remoteSubTaskInterval.postFailWith = null

            f.syncCoordinator.syncPendingOperations() // project still down: nothing drains
            f.goOnline()
            f.syncCoordinator.syncPendingOperations()

            assertThat(f.remoteProject.postedProjectIds).isEqualTo(listOf("p1"))
            assertThat(f.remoteTask.postedTaskIds).isEqualTo(listOf("t1"))
            assertThat(f.remoteInterval.postedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(f.remoteSubTask.postedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(f.remoteSubTaskInterval.postedIntervalIds).isEqualTo(listOf("si1"))
            assertThat(f.queue.all().isEmpty()).isTrue()
        }
}
