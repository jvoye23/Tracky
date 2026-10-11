package com.jvcs.tracky.features.projecttracker.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Instant

/**
 * A free device is local-only: with the cloud-sync gate closed, every write lands in Room and
 * nothing else happens. Nothing is pushed, queued for later or scheduled.
 *
 * Each repository asks the gate itself, so each is pinned here separately. A check that one of them
 * forgot would leak a free user's data to the server, or fill an outbox that the import, run once
 * they sign in, would then replay on top of itself.
 */
internal class FreeTierGateTest {

    private fun freeDevice() = RepoFixture().apply { cloudSyncGate.enabled.value = false }

    private fun RepoFixture.assertNothingLeftTheDevice() {
        assertThat(
            remoteProject.postedProjectIds + remoteProject.updatedProjectIds + remoteProject.deletedProjectIds,
        ).isEmpty()
        assertThat(remoteTask.postedTaskIds + remoteTask.updatedTaskIds + remoteTask.deletedTaskIds).isEmpty()
        assertThat(
            remoteInterval.postedIntervalIds + remoteInterval.updatedIntervalIds + remoteInterval.deletedIntervalIds,
        ).isEmpty()
        assertThat(
            remoteSubTask.postedSubTaskIds + remoteSubTask.updatedSubTaskIds + remoteSubTask.deletedSubTaskIds,
        ).isEmpty()
        assertThat(
            remoteSubTaskInterval.postedIntervalIds +
                remoteSubTaskInterval.updatedIntervalIds +
                remoteSubTaskInterval.deletedIntervalIds,
        ).isEmpty()
        assertThat(
            remoteProject.reorderCalls + remoteTask.reorderCalls + remoteSubTask.reorderCalls,
        ).isEmpty()
        assertThat(queue.all()).isEmpty()
        assertThat(scheduler.scheduleCount).isEqualTo(0)
    }

    @Test
    fun aNewProjectStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()

            val result = f.projectRepository.upsertProject(f.db.newProject("p1"))

            assertThat(result).isInstanceOf<Result.Success<Unit>>()
            assertThat(f.db.projects.keys).contains("p1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun deletingAProjectStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.db.seedProject("p1")

            f.projectRepository.deleteProject("p1")

            assertThat(f.db.projects.keys).doesNotContain("p1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun aNewTaskStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.db.seedProject("p1")

            f.taskRepository.upsertProjectTask(f.db.newTask("t1", "p1"))

            assertThat(f.db.tasks.keys).contains("t1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun trackedTimeStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTask()

            f.createIntervalForTask()
            f.closeIntervalForTask()

            assertThat(f.db.intervals.values).isNotEmpty()
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun aNewSubTaskStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTask()

            f.subTaskRepository.upsertSubTask(f.db.newSubTask("s1", "t1", "p1"))

            assertThat(f.db.subTasks.keys).contains("s1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun subTaskTimeStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTaskAndSubTask()

            f.subTaskIntervalRepository.createSubTaskInterval(f.db.newSubTaskInterval("si1", "s1", "ti1", "p1"))

            assertThat(f.db.subTaskIntervals.keys).contains("si1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun deletingATaskStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTask()

            f.taskRepository.deleteProjectTask("p1", "t1")

            assertThat(f.db.tasks.keys).doesNotContain("t1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun reorderingTasksStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.db.seedProject("p1")
            f.db.seedTask("a", "p1")
            f.db.seedTask("b", "p1")

            f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            f.assertNothingLeftTheDevice()
        }

    @Test
    fun deletingTrackedTimeStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTask()
            f.createIntervalForTask()
            f.closeIntervalForTask()
            val intervalId =
                f.db.intervals.keys
                    .single()

            f.intervalRepository.deleteTaskInterval(intervalId)

            assertThat(f.db.intervals.keys).doesNotContain(intervalId)
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun deletingASubTaskStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTaskAndSubTask()

            f.subTaskRepository.deleteSubTask("s1")

            assertThat(f.db.subTasks.keys).doesNotContain("s1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun reorderingSubTasksStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTask()
            f.db.seedSubTask("a", "t1", "p1")
            f.db.seedSubTask("b", "t1", "p1")

            f.subTaskRepository.reorderSubTasks("t1", listOf("b", "a"))

            f.assertNothingLeftTheDevice()
        }

    @Test
    fun deletingSubTaskTimeStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.seedProjectWithTaskAndSubTask()
            f.db.seedSubTaskInterval("si1", "s1", "ti1", "p1")

            f.subTaskIntervalRepository.deleteSubTaskInterval("si1")

            assertThat(f.db.subTaskIntervals.keys).doesNotContain("si1")
            f.assertNothingLeftTheDevice()
        }

    @Test
    fun reorderingProjectsStaysOnTheDevice() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.db.seedProject("p1")
            f.db.seedProject("p2")

            f.organizationRepository.reorderProjects(listOf("p2", "p1"))

            f.assertNothingLeftTheDevice()
        }

    @Test
    fun theDrainPushesNothingWhileTheGateIsClosed() =
        runBlocking<Unit> {
            val f = freeDevice()
            f.db.seedProject("p1")
            // A leftover from before the device went local-only, e.g. an account that just signed out.
            f.queue.enqueue(
                entityId = "p1",
                entityType = PendingSyncOperation.ENTITY_PROJECT,
                operationType = PendingSyncOperation.OP_UPDATE,
                parentEntityId = null,
                createdAt = Instant.fromEpochMilliseconds(0),
            )

            f.syncCoordinator.syncPendingOperations()

            assertThat(f.remoteProject.updatedProjectIds).isEmpty()
            assertThat(f.queue.all()).hasSize(1)
        }

    @Test
    fun anOpenGateLetsTheSameWriteThrough() =
        runBlocking<Unit> {
            // The control: the assertions above fail for the gate, not because the fixture never pushes.
            val f = RepoFixture()

            f.projectRepository.upsertProject(f.db.newProject("p1"))

            assertThat(f.remoteProject.postedProjectIds).containsExactly("p1")
        }

    @Test
    fun anOpenGateLetsTheSameReordersThrough() =
        runBlocking<Unit> {
            // The control for the reorders, whose fixtures are the least obvious to get right.
            val f = RepoFixture()
            f.seedProjectWithTask()
            f.db.seedProject("p2")
            f.db.seedTask("t2", "p1")
            f.db.seedSubTask("a", "t1", "p1")
            f.db.seedSubTask("b", "t1", "p1")

            f.organizationRepository.reorderProjects(listOf("p2", "p1"))
            f.taskRepository.reorderTasks("p1", listOf("t2", "t1"))
            f.subTaskRepository.reorderSubTasks("t1", listOf("b", "a"))

            assertThat(f.remoteProject.reorderCalls).hasSize(1)
            assertThat(f.remoteTask.reorderCalls).hasSize(1)
            assertThat(f.remoteSubTask.reorderCalls).hasSize(1)
        }
}
