@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

            assertEquals(1, f.localTask.sortIndexWrites.size, "a drag is one local write")
            assertEquals(mapOf("c" to 0L, "a" to 1L, "b" to 2L), f.localTask.sortIndexWrites.single())
        }

    @Test
    fun reorderTasks_skipsTheTasksThatDidNotMove() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.taskRepository.reorderTasks("p1", listOf("a", "b", "c"))
            f.localTask.sortIndexWrites.clear()

            // Swapping the last two leaves "a" at index 0, so it must not be rewritten.
            f.taskRepository.reorderTasks("p1", listOf("a", "c", "b"))

            assertEquals(mapOf("c" to 1L, "b" to 2L), f.localTask.sortIndexWrites.single())
        }

    @Test
    fun reorderTasks_isANoOpWhenNothingMoved() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.taskRepository.reorderTasks("p1", listOf("a", "b"))
            f.localTask.sortIndexWrites.clear()
            f.remoteTask.reorderCalls.clear()

            val result = f.taskRepository.reorderTasks("p1", listOf("a", "b"))

            assertTrue(result is Result.Success)
            assertTrue(f.localTask.sortIndexWrites.isEmpty(), "an unchanged order costs no write")
            assertTrue(f.remoteTask.reorderCalls.isEmpty(), "an unchanged order costs no request")
        }

    @Test
    fun reorderTasks_ignoresIdsThatAreNotInTheProject() =
        runBlocking<Unit> {
            // A card deleted on another device can still be in the list the drag settled on.
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))

            f.taskRepository.reorderTasks("p1", listOf("ghost", "a", "b"))

            assertEquals(mapOf("a" to 1L, "b" to 2L), f.localTask.sortIndexWrites.single())
        }

    @Test
    fun reorderTasks_pushesTheWholeGestureAsOneRequest() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))

            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))

            assertEquals(1, f.remoteTask.reorderCalls.size)
            assertEquals(mapOf("c" to 0L, "b" to 1L, "a" to 2L), f.remoteTask.reorderCalls.single())
            assertTrue(f.remoteTask.taskRoutes.contains("p1/sort"))
        }

    @Test
    fun reorderTasks_stampsOneTimestampFromTheInjectedClock() =
        runBlocking<Unit> {
            // Reading the clock twice would stamp the local rows and the server rows with different
            // values for what is a single reorder.
            val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(5_000))
            val f = fixtureWithTasks(time, "a", "b")

            f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertEquals(1, f.localTask.sortIndexWrites.size)
            assertEquals(1, f.remoteTask.reorderCalls.size)
        }

    @Test
    fun reorderTasks_doesNotCallTheServerWhenTheLocalWriteFails() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.localTask.sortIndexWriteFailWith = DataError.Local.DISK_FULL

            val result = f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertTrue(result is Result.Error)
            assertTrue(f.remoteTask.reorderCalls.isEmpty(), "nothing was persisted, so nothing to push")
        }

    @Test
    fun reorderTasks_queuesOneMarkerWhenOffline() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET

            // The user sees success: the local write landed and the push is queued.
            val result = f.taskRepository.reorderTasks("p1", listOf("b", "a"))

            assertTrue(result is Result.Success)
            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER }
            assertEquals(1, ops.size)
            assertEquals("p1", ops.single().parentEntityId)
            assertTrue(f.scheduler.scheduleCount > 0)
        }

    @Test
    fun reorderTasks_collapsesRepeatOfflineReordersIntoOneQueueRow() =
        runBlocking<Unit> {
            val f = fixtureWithTasks(taskIds = arrayOf("a", "b", "c"))
            f.remoteTask.reorderFailWith = DataError.Remote.NO_INTERNET

            f.taskRepository.reorderTasks("p1", listOf("b", "a", "c"))
            f.taskRepository.reorderTasks("p1", listOf("c", "b", "a"))

            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER }
            assertEquals(1, ops.size, "the queue row is a marker, not a payload")
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
            assertEquals(1, orderOps.size, "the reorder must survive alongside the project's own op")
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

            assertEquals(mapOf("c" to 0L, "b" to 1L, "a" to 2L), f.remoteTask.reorderCalls.single())
            assertTrue(
                f.queue.all().none { it.entityType == PendingSyncOperation.ENTITY_TASK_ORDER },
                "a drained marker should be removed",
            )
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

            assertEquals(mapOf("c" to 0L, "a" to 2L), f.remoteTask.reorderCalls.single())
        }
}
