package com.jvcs.tracky.features.project.data.subtaskinterval

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project_tracker.data.RepoFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
        startedParentTimer: Boolean = false,
    ) = fixture.db.newSubTaskInterval(id, subTaskId, taskIntervalId, "p1", startedParentTimer)

    @Test
    fun theRouteIsBuiltFromTheParentSubTaskBecauseTheIntervalHasNoTaskId() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()

            val result = repo.createSubTaskInterval(interval())

            assertThat(result is Result.Success).isTrue()
            // p1 and si1 come off the interval; t1 could only have come from the subtask row.
            assertThat(remote.intervalRoutes).isEqualTo(listOf("p1/t1/s1"))
            assertThat(remote.postedIntervalIds).isEqualTo(listOf("si1"))
        }

    @Test
    fun theLocalOnlyFieldsSurviveASuccessfulPush() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()

            repo.createSubTaskInterval(interval(startedParentTimer = true))

            // The server has a column for neither, and the happy path writes its echo straight back to
            // Room — where parentTaskIntervalId is a NOT NULL foreign key and startedParentTimer is
            // what decides whether stopping this subtask also stops its parent task.
            val stored = fixture.db.subTaskIntervals.getValue("si1")
            assertThat(stored.parentTaskIntervalId).isEqualTo("ti1")
            assertThat(stored.startedParentTimer).isTrue()
        }

    @Test
    fun anIntervalUnderAPendingSubTaskIsQueuedWithoutSpendingARequest() =
        runTest {
            fixture.seedProjectWithTask()
            // The subtask was created offline: there is no .../subtasks/s1/intervals route yet.
            fixture.remoteSubTask.postFailWith = DataError.Remote.NO_INTERNET
            fixture.subTaskRepository.upsertSubTask(fixture.db.newSubTask("s1", "t1", "p1"))

            repo.createSubTaskInterval(interval())

            assertThat(remote.intervalRoutes.isEmpty()).isTrue()
            val op = queue.all().single { it.entityType == PendingSyncOperation.ENTITY_SUBTASK_INTERVAL }
            assertThat(op.operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
            assertThat(op.parentEntityId).isEqualTo("s1")
        }

    @Test
    fun aDuplicateCreateIsRetriedAsAnUpdateNotResolvedByLastWriteWins() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            // The POST landed and only its response was lost. An interval carries no stamp of its own,
            // so there is nothing to compare — pushing local state as an update is the whole fix.
            remote.postFailWith = DataError.Remote.CONFLICT

            val result = repo.createSubTaskInterval(interval())

            assertThat(result is Result.Success).isTrue()
            assertThat(remote.updatedIntervalIds).isEqualTo(listOf("si1"))
        }

    @Test
    fun notFoundIsQueuedRatherThanDropped() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            // Dropping here would silently lose tracked time — the exact case this feature exists for.
            remote.postFailWith = DataError.Remote.NOT_FOUND

            repo.createSubTaskInterval(interval())

            assertThat(queue.all().single().entityId).isEqualTo("si1")
        }

    @Test
    fun aTransientFailureQueuesTheWriteAndWakesTheScheduler() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET

            val result = repo.createSubTaskInterval(interval())

            // The local row already stands, so queuing is the success path.
            assertThat(result is Result.Success).isTrue()
            assertThat(queue.all().single().entityType).isEqualTo(PendingSyncOperation.ENTITY_SUBTASK_INTERVAL)
            assertThat(fixture.scheduler.scheduleCount > 0).isTrue()
        }

    @Test
    fun theDrainPushesQueuedIntervalsAndClearsThem() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET
            repo.createSubTaskInterval(interval())
            remote.postFailWith = null

            repo.syncPendingSubTaskIntervals()

            assertThat(remote.postedIntervalIds).isEqualTo(listOf("si1"))
            assertThat(queue.all().isEmpty()).isTrue()
        }

    @Test
    fun deletingAnIntervalThatNeverReachedTheServerJustDropsTheQueue() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET
            repo.createSubTaskInterval(interval())
            remote.postFailWith = null
            remote.intervalRoutes.clear()

            repo.deleteSubTaskInterval("si1")

            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.intervalRoutes.isEmpty()).isTrue()
        }

    @Test
    fun aQueuedDeleteCarriesTheSubTaskIdBecauseTheRowIsGoneByDrainTime() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            fixture.db.seedSubTaskInterval("si1", "s1", "ti1", "p1")
            remote.deleteFailWith = DataError.Remote.SERVER_ERROR

            repo.deleteSubTaskInterval("si1")

            val op = queue.all().single()
            assertThat(op.operationType).isEqualTo(PendingSyncOperation.OP_DELETE)
            // Both remaining route segments are recovered from this one link.
            assertThat(op.parentEntityId).isEqualTo("s1")
        }

    @Test
    fun aQueuedDeleteWhoseSubTaskIsAlreadyGoneIsDroppedNotRetriedForever() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            fixture.db.seedSubTaskInterval("si1", "s1", "ti1", "p1")
            remote.deleteFailWith = DataError.Remote.SERVER_ERROR
            repo.deleteSubTaskInterval("si1")
            remote.deleteFailWith = null
            // Deleting a subtask cascades to its intervals server-side, so this op has nothing left to
            // do — and the route it would need is unrecoverable.
            fixture.db.cascadeDeleteSubTask("s1")

            repo.syncPendingSubTaskIntervals()

            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.deletedIntervalIds.isEmpty()).isTrue()
        }
}
