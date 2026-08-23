@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.isMissingOrForbidden
import com.jvcs.tracky.core.domain.util.isTransient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * The API answers "not there, or not yours" with `403`, not `404` — everywhere except creating a
 * subtask interval, which is the only `404` it returns
 * (Requirements/api/backend_documentation.md).
 *
 * Sync used to branch on `NOT_FOUND` alone, which made every one of those branches unreachable
 * against the real server. `403` fell through to the permanent-error case instead: a write whose
 * parent was missing was neither retried nor queued, so it silently never reached the server, and a
 * delete of a row the server had already dropped surfaced as a failure. These pin both directions.
 */
internal class ForbiddenIsAMissTest {

    @Test
    fun bothStatusesCountAsAMiss() {
        assertTrue(DataError.Remote.FORBIDDEN.isMissingOrForbidden())
        assertTrue(DataError.Remote.NOT_FOUND.isMissingOrForbidden())
        // A miss is not a reason to retry — the drain still drops it rather than looping.
        assertFalse(DataError.Remote.FORBIDDEN.isTransient())
        assertFalse(DataError.Remote.CONFLICT.isMissingOrForbidden())
        assertFalse(DataError.Remote.BAD_REQUEST.isMissingOrForbidden())
    }

    @Test
    fun aTaskWhoseProjectTheServerDeniesIsQueued_notDropped() = runBlocking<Unit> {
        val f = RepoFixture()
        f.db.seedProject("p1")
        f.remoteTask.failWith = DataError.Remote.FORBIDDEN

        f.taskRepository.upsertProjectTask(f.db.newTask("t1", "p1"))

        // Before the fix this fell through to the permanent-error branch and the task was lost.
        assertEquals(PendingSyncOperation.OP_CREATE, f.queue.all().single().operationType)
    }

    @Test
    fun aSubTaskWhoseTaskTheServerDeniesIsQueued_notDropped() = runBlocking<Unit> {
        val f = RepoFixture()
        f.seedProjectWithTask()
        f.remoteSubTask.postFailWith = DataError.Remote.FORBIDDEN

        f.subTaskRepository.upsertSubTask(f.db.newSubTask("s1", "t1", "p1"))

        assertEquals("s1", f.queue.all().single().entityId)
    }

    @Test
    fun aSubTaskIntervalWhoseSubTaskTheServerDeniesIsQueued_notDropped() = runBlocking<Unit> {
        val f = RepoFixture()
        f.seedProjectWithTaskAndSubTask()
        f.remoteSubTaskInterval.postFailWith = DataError.Remote.FORBIDDEN

        f.subTaskIntervalRepository.createSubTaskInterval(
            f.db.newSubTaskInterval("si1", "s1", "ti1", "p1")
        )

        // Tracked time is exactly what must never be dropped on a miss.
        assertEquals("si1", f.queue.all().single().entityId)
    }

    @Test
    fun deletingASubTaskTheServerAlreadyDroppedReportsSuccess() = runBlocking<Unit> {
        val f = RepoFixture()
        f.seedProjectWithTaskAndSubTask()
        f.remoteSubTask.deleteFailWith = DataError.Remote.FORBIDDEN

        val result = f.subTaskRepository.deleteSubTask("s1")

        // The local delete stands and there is nothing left to push, so this is not a failure.
        assertTrue(result is Result.Success, "was $result")
        assertTrue(f.queue.all().isEmpty())
    }

    @Test
    fun deletingATaskTheServerAlreadyDroppedReportsSuccess() = runBlocking<Unit> {
        val f = RepoFixture()
        f.seedProjectWithTask()
        f.remoteTask.failWith = DataError.Remote.FORBIDDEN

        val result = f.taskRepository.deleteProjectTask("p1", "t1")

        assertTrue(result is Result.Success, "was $result")
        assertTrue(f.queue.all().isEmpty())
    }
}
