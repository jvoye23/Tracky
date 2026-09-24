@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.ExperimentalTime

/**
 * [ReorderTasksTest]'s contract one level down, scoped to a single parent task: a subtask only ever
 * reorders among its siblings, so its parent never changes for the whole gesture.
 */
internal class ReorderSubTasksTest {

    private fun fixture(vararg subTaskIds: String) =
        RepoFixture().apply {
            seedProjectWithTask()
            subTaskIds.forEach { db.seedSubTask(it, "t1", "p1") }
        }

    @Test
    fun reorderSubTasks_writesOnlyTheIdsWhoseIndexActuallyMoved() =
        runBlocking<Unit> {
            val f = fixture("a", "b", "c")
            f.subTaskRepository.reorderSubTasks("t1", listOf("a", "b", "c"))
            f.localSubTask.sortIndexWrites.clear()

            f.subTaskRepository.reorderSubTasks("t1", listOf("a", "c", "b"))

            assertThat(f.localSubTask.sortIndexWrites.single()).isEqualTo(mapOf("c" to 1L, "b" to 2L))
        }

    @Test
    fun reorderSubTasks_pushesTheWholeGestureAsOneRequestUnderTheParentTask() =
        runBlocking<Unit> {
            val f = fixture("a", "b")

            f.subTaskRepository.reorderSubTasks("t1", listOf("b", "a"))

            assertThat(f.remoteSubTask.reorderCalls.single()).isEqualTo(mapOf("b" to 0L, "a" to 1L))
            assertThat(f.remoteSubTask.subTaskRoutes.contains("p1/t1/sort")).isTrue()
        }

    @Test
    fun reorderSubTasks_onlyTouchesTheSiblingsOfOneTask() =
        runBlocking<Unit> {
            // A subtask never leaves its parent, so another task's subtasks must not be re-indexed.
            val f = fixture("a", "b")
            f.db.seedTask("t2", "p1")
            f.db.seedSubTask("other", "t2", "p1")

            f.subTaskRepository.reorderSubTasks("t1", listOf("b", "a"))

            assertThat(
                f.localSubTask.sortIndexWrites
                    .single()
                    .keys,
            ).isEqualTo(setOf("b", "a"))
        }

    @Test
    fun reorderSubTasks_queuesOneMarkerWhenOffline() =
        runBlocking<Unit> {
            val f = fixture("a", "b")
            f.remoteSubTask.reorderFailWith = DataError.Remote.NO_INTERNET

            val result = f.subTaskRepository.reorderSubTasks("t1", listOf("b", "a"))

            assertThat(result is Result.Success).isTrue()
            val ops = f.queue.all().filter { it.entityType == PendingSyncOperation.ENTITY_SUBTASK_ORDER }
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops.single().parentEntityId).isEqualTo("t1")
        }

    @Test
    fun syncPendingSubTasks_rebuildsTheOrderFromLocalStateWhenTheMarkerDrains() =
        runBlocking<Unit> {
            val f = fixture("a", "b", "c")
            f.remoteSubTask.reorderFailWith = DataError.Remote.NO_INTERNET
            f.subTaskRepository.reorderSubTasks("t1", listOf("c", "a", "b"))
            f.remoteSubTask.reorderFailWith = null
            f.remoteSubTask.reorderCalls.clear()

            f.subTaskRepository.syncPendingSubTasks()

            assertThat(f.remoteSubTask.reorderCalls.single()).isEqualTo(mapOf("c" to 0L, "a" to 1L, "b" to 2L))
        }
}
