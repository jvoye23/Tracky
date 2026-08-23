package com.jvcs.tracky.features.project.data.subtaskinterval

import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project_tracker.data.RepoFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The deepest row in the tree, and the only one whose route cannot be built from itself.
 *
 * A SubTaskInterval carries its project and subtask ids but never its task id, so every call has to
 * resolve that from the parent subtask row. Most cases here are about what happens when that
 * lookup, or an ancestor's own push, has not landed yet.
 */
internal class OfflineFirstSubTaskIntervalRepositoryTest {

    private val fixture = RepoFixture()
    private val repo get() = fixture.subTaskIntervalRepository
    private val remote get() = fixture.remoteSubTaskInterval
    private val queue get() = fixture.queue

    private fun interval(
        id: String = "si1",
        subTaskId: String = "s1",
        taskIntervalId: String = "ti1",
        startedParentTimer: Boolean = false
    ) = fixture.db.newSubTaskInterval(id, subTaskId, taskIntervalId, "p1", startedParentTimer)

    @Test
    fun theRouteIsBuiltFromTheParentSubTaskBecauseTheIntervalHasNoTaskId() = runTest {
        fixture.seedProjectWithTaskAndSubTask()

        val result = repo.createSubTaskInterval(interval())

        assertTrue(result is Result.Success)
        // p1 and si1 come off the interval; t1 could only have come from the subtask row.
        assertEquals(listOf("p1/t1/s1"), remote.intervalRoutes)
        assertEquals(listOf("si1"), remote.postedIntervalIds)
    }

    @Test
    fun theLocalOnlyFieldsSurviveASuccessfulPush() = runTest {
        fixture.seedProjectWithTaskAndSubTask()

        repo.createSubTaskInterval(interval(startedParentTimer = true))

        // The server has a column for neither, and the happy path writes its echo straight back to
        // Room — where parentTaskIntervalId is a NOT NULL foreign key and startedParentTimer is
        // what decides whether stopping this subtask also stops its parent task.
        val stored = fixture.db.subTaskIntervals.getValue("si1")
        assertEquals("ti1", stored.parentTaskIntervalId)
        assertTrue(stored.startedParentTimer)
    }

    @Test
    fun anIntervalUnderAPendingSubTaskIsQueuedWithoutSpendingARequest() = runTest {
        fixture.seedProjectWithTask()
        // The subtask was created offline: there is no .../subtasks/s1/intervals route yet.
        fixture.remoteSubTask.postFailWith = DataError.Remote.NO_INTERNET
        fixture.subTaskRepository.upsertSubTask(fixture.db.newSubTask("s1", "t1", "p1"))

        repo.createSubTaskInterval(interval())

        assertTrue(remote.intervalRoutes.isEmpty())
        val op = queue.all().single { it.entityType == PendingSyncOperation.ENTITY_SUBTASK_INTERVAL }
        assertEquals(PendingSyncOperation.OP_CREATE, op.operationType)
        assertEquals("s1", op.parentEntityId)
    }

    @Test
    fun aDuplicateCreateIsRetriedAsAnUpdateNotResolvedByLastWriteWins() = runTest {
        fixture.seedProjectWithTaskAndSubTask()
        // The POST landed and only its response was lost. An interval carries no stamp of its own,
        // so there is nothing to compare — pushing local state as an update is the whole fix.
        remote.postFailWith = DataError.Remote.CONFLICT

        val result = repo.createSubTaskInterval(interval())

        assertTrue(result is Result.Success)
        assertEquals(listOf("si1"), remote.updatedIntervalIds)
    }

    @Test
    fun notFoundIsQueuedRatherThanDropped() = runTest {
        fixture.seedProjectWithTaskAndSubTask()
        // Dropping here would silently lose tracked time — the exact case this feature exists for.
        remote.postFailWith = DataError.Remote.NOT_FOUND

        repo.createSubTaskInterval(interval())

        assertEquals("si1", queue.all().single().entityId)
    }

    @Test
    fun aTransientFailureQueuesTheWriteAndWakesTheScheduler() = runTest {
        fixture.seedProjectWithTaskAndSubTask()
        remote.postFailWith = DataError.Remote.NO_INTERNET

        val result = repo.createSubTaskInterval(interval())

        // The local row already stands, so queuing is the success path.
        assertTrue(result is Result.Success)
        assertEquals(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL, queue.all().single().entityType)
        assertTrue(fixture.scheduler.scheduleCount > 0)
    }
}
