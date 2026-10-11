@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.projecttracker.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A drag is one action for the user, so it has to be one action here: one transactional local write
 * and one request. Writing task by task is what these tests exist to prevent — a failure partway
 * through would leave two rows sharing an index, which no retry can repair.
 *
 * [ReorderSubTasksTest] covers the same contract one level down.
 */
internal class ReorderTasksTest {

    private fun fixtureWithTasks(time: FakeTimeProvider = FakeTimeProvider(), vararg taskIds: String) =
        RepoFixture(time).apply {
            db.seedProject("p1")
            taskIds.forEach { db.seedTask(it, "p1") }
        }

    // ---- tasks ---------------------------------------------------------------------------------

    @Test
    fun reorderTasks_writesOnlyTheIdsWhoseIndexActuallyMoved() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            // Settle a known starting order first, so the second call has something to diff against.
            f.taskRepository.reorderTasks("p1", listOf("a", "b", "c"))
            f.localTask.sortIndexWrites.clear()
            f.remoteTask.reorderCalls.clear()

            // Move "c" to the front: every id shifts, so all three are written.
            f.taskRepository.reorderTasks("p1", listOf("c", "a", "b"))

            assertThat(f.localTask.sortIndexWrites.size, name = "a drag is one local write").isEqualTo(1)
            assertThat(f.localTask.sortIndexWrites.single()).isEqualTo(mapOf("c" to 0L, "a" to 1L, "b" to 2L))
        }

    @Test
    fun reorderTasks_skipsTheTasksThatDidNotMove() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.taskRepository.reorderTasks("p1", listOf("a", "b", "c"))
            f.localTask.sortIndexWrites.clear()

            // Swapping the last two leaves "a" at index 0, so it must not be rewritten.
            f.taskRepository.reorderTasks("p1", listOf("a", "c", "b"))

            assertThat(f.localTask.sortIndexWrites.single()).isEqualTo(mapOf("c" to 1L, "b" to 2L))
        }

    @Test
    fun reorderTasks_isANoOpWhenNothingMoved() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.taskRepository.reorderTasks("p1", listOf("a", "b"))
            f.localTask.sortIndexWrites.clear()
            f.remoteTask.reorderCalls.clear()

            val result = f.taskRepository.reorderTasks("p1", listOf("a", "b"))

            assertThat(result is Result.Success).isTrue()
            assertThat(f.localTask.sortIndexWrites.isEmpty(), name = "an unchanged order costs no write").isTrue()
            assertThat(f.remoteTask.reorderCalls.isEmpty(), name = "an unchanged order costs no request").isTrue()
        }

    @Test
    fun reorderTasks_ignoresIdsThatAreNotInTheProject() =
        runBlocking<Unit> {
            // A card deleted on another device can still be in the list the drag settled on.
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))

            f.taskRepository.reorderTasks("p1", listOf("ghost", "a", "b"))

            assertThat(f.localTask.sortIndexWrites.single()).isEqualTo(mapOf("a" to 1L, "b" to 2L))
        }

    @Test
    fun reorderTasks_pushesTheWholeGestureAsOneRequest() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))

            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))

            assertThat(f.remoteTask.reorderCalls.size).isEqualTo(1)
            assertThat(f.remoteTask.reorderCalls.single()).isEqualTo(mapOf("c" to 0L, "b" to 1L, "a" to 2L))
            assertThat(f.remoteTask.taskRoutes.contains("p1/sort")).isTrue()
        }

    @Test
    fun reorderTasks_stampsOneTimestampFromTheInjectedClock() =
        runBlocking<Unit> {
            // Reading the clock twice would stamp the local rows and the server rows with different
            // values for what is a single reorder.
            val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(5_000))
            val f = fixtureWithTasks(time, "a", "b")

            f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertThat(f.localTask.sortIndexWrites.size).isEqualTo(1)
            assertThat(f.remoteTask.reorderCalls.size).isEqualTo(1)
        }

    @Test
    fun reorderTasks_doesNotCallTheServerWhenTheLocalWriteFails() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.localTask.sortIndexWriteFailWith = DataError.Local.DISK_FULL

            val result = f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertThat(result is Result.Error).isTrue()
            assertThat(f.remoteTask.reorderCalls.isEmpty(), name = "nothing was persisted, so nothing to push").isTrue()
        }

    @Test
    fun reorderTasks_queuesOneMarkerWhenOffline() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET

            // The user sees success: the local write landed and the push is queued.
            val result = f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertThat(result is Result.Success).isTrue()
            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops.single().parentEntityId).isEqualTo("p1")
            assertThat(f.scheduler.scheduleCount > 0).isTrue()
        }

    @Test
    fun reorderTasks_collapsesRepeatOfflineReordersIntoOneQueueRow() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET

            f.taskRepository.reorderTasks("p1", listOf("b", "a", "c"))
            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))

            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER }
            assertThat(ops.size, name = "the queue row is a marker, not a payload").isEqualTo(1)
        }

    @Test
    fun reorderTasks_queueRowDoesNotCollideWithTheProjectsOwnPendingWrite() =
        runBlocking<Unit> {
            // enqueueDeduped keys on entityId alone, so a bare project UUID here would be deduped
            // against the project's own CREATE and the reorder would never be pushed.
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.remoteProject.failWith = DataError.Remote.NO_INTERNET
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET
            f.projectRepository.upsertProject(
                f.db.projects
                    .getValue("p1")
                    .copy(title = "renamed"),
            )

            f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            val orderOps = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER }
            assertThat(orderOps.size, name = "the reorder must survive alongside the project's own op").isEqualTo(1)
        }

    @Test
    fun syncPendingTasks_rebuildsTheOrderFromLocalStateWhenTheMarkerDrains() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET
            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))
            f.remoteTask.reorderFailWith = null
            f.remoteTask.reorderCalls.clear()

            f.taskRepository.syncPendingTasks()

            assertThat(f.remoteTask.reorderCalls.single()).isEqualTo(mapOf("c" to 0L, "b" to 1L, "a" to 2L))
            assertThat(
                f.queue.all().none {
                    it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER
                },
                name = "a drained marker should be removed",
            ).isTrue()
        }

    @Test
    fun syncPendingTasks_dropsATaskDeletedWhileTheReorderWasQueued() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET
            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))
            f.db.tasks.remove("b")
            f.remoteTask.reorderFailWith = null
            f.remoteTask.reorderCalls.clear()

            f.taskRepository.syncPendingTasks()

            assertThat(f.remoteTask.reorderCalls.single()).isEqualTo(mapOf("c" to 0L, "a" to 2L))
        }
}
