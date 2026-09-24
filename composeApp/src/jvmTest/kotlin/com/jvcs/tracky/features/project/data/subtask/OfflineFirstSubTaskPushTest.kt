package com.jvcs.tracky.features.project.data.subtask

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project_tracker.data.RepoFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * What a subtask write sends, and what it queues when it cannot send.
 *
 * Subtasks are the fourth level of a tree addressed entirely through its ancestors
 * (`/api/projects/{projectId}/tasks/{taskId}/subtasks`), so every case here is really about what
 * happens when an ancestor is not on the server yet — the situation the offline queue exists for.
 */
internal class OfflineFirstSubTaskPushTest {

    private val fixture = RepoFixture()
    private val repo get() = fixture.subTaskRepository
    private val remote get() = fixture.remoteSubTask
    private val queue get() = fixture.queue

    private fun newSubTask(
        id: String = "s1",
        taskId: String = "t1",
        projectId: String = "p1",
    ) = fixture.db.newSubTask(id, taskId, projectId)

    @Test
    fun creatingASubTaskPostsItOnTheRouteBuiltFromItsAncestors() =
        runTest {
            fixture.seedProjectWithTask()

            val result = repo.upsertSubTask(newSubTask())

            assertThat(result is Result.Success).isTrue()
            assertThat(remote.postedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(remote.subTaskRoutes).isEqualTo(listOf("p1/t1"))
            assertThat(queue.all().isEmpty()).isTrue()
        }

    @Test
    fun aSubTaskWriteStampsTheRowSoLastWriteWinsHasSomethingToCompare() =
        runTest {
            fixture.seedProjectWithTask()

            repo.upsertSubTask(newSubTask())

            // Unlike an interval, a subtask is edited by hand and carries a real stamp.
            assertThat(
                fixture.db.subTasks
                    .getValue("s1")
                    .ownUpdatedAt,
            ).isEqualTo(fixture.time.now)
        }

    @Test
    fun aTransientFailureQueuesTheCreateAndWakesTheScheduler() =
        runTest {
            fixture.seedProjectWithTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET

            val result = repo.upsertSubTask(newSubTask())

            // The local row already stands, so queuing is the success path.
            assertThat(result is Result.Success).isTrue()
            val op = queue.all().single()
            assertThat(op.entityType).isEqualTo(PendingSyncOperation.ENTITY_SUBTASK)
            assertThat(op.operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
            assertThat(op.entityId).isEqualTo("s1")
            assertThat(fixture.scheduler.scheduleCount > 0).isTrue()
        }

    @Test
    fun aSubTaskUnderAPendingTaskIsQueuedWithoutSpendingARequest() =
        runTest {
            fixture.db.seedProject("p1")
            // The task was created offline: there is no .../tasks/t1/subtasks route to POST to yet, so
            // its CREATE is still sitting in the queue.
            fixture.remoteTask.postFailWith = DataError.Remote.NO_INTERNET
            fixture.taskRepository.upsertProjectTask(fixture.db.newTask("t1", "p1"))

            repo.upsertSubTask(newSubTask())

            assertThat(remote.subTaskRoutes.isEmpty()).isTrue()
            assertThat(
                queue
                    .all()
                    .single {
                        it.entityType == PendingSyncOperation.ENTITY_SUBTASK
                    }.operationType,
            ).isEqualTo(PendingSyncOperation.OP_CREATE)
        }

    @Test
    fun notFoundIsQueuedRatherThanDropped() =
        runTest {
            fixture.seedProjectWithTask()
            // The parent task is missing server-side after all. Dropping would lose the subtask; the
            // drain runs tasks first, so a retry can still succeed.
            remote.postFailWith = DataError.Remote.NOT_FOUND

            repo.upsertSubTask(newSubTask())

            assertThat(queue.all().single().entityId).isEqualTo("s1")
        }

    @Test
    fun aConflictOnCreateIsResolvedByLastWriteWins() =
        runTest {
            fixture.seedProjectWithTask()
            remote.postFailWith = DataError.Remote.CONFLICT
            // The server's copy is older, so the local row wins and is pushed as an update.
            remote.serverSubTasks = listOf(newSubTask().copy(title = "server", ownUpdatedAt = null))

            repo.upsertSubTask(newSubTask().copy(title = "local"))

            assertThat(remote.updatedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(
                fixture.db.subTasks
                    .getValue("s1")
                    .title,
            ).isEqualTo("local")
        }

    @Test
    fun aConflictAgainstANewerServerRowAdoptsTheServerCopy() =
        runTest {
            fixture.seedProjectWithTask()
            remote.postFailWith = DataError.Remote.CONFLICT
            remote.serverSubTasks =
                listOf(
                    newSubTask().copy(title = "server", ownUpdatedAt = fixture.time.now + 1.milliseconds),
                )

            repo.upsertSubTask(newSubTask().copy(title = "local"))

            assertThat(remote.updatedSubTaskIds.isEmpty()).isTrue()
            assertThat(
                fixture.db.subTasks
                    .getValue("s1")
                    .title,
            ).isEqualTo("server")
        }

    @Test
    fun deletingASubTaskThatNeverReachedTheServerJustDropsTheQueue() =
        runTest {
            fixture.seedProjectWithTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET
            repo.upsertSubTask(newSubTask())
            remote.postFailWith = null
            remote.subTaskRoutes.clear()

            repo.deleteSubTask("s1")

            // A ghost: created offline and deleted before it ever existed remotely.
            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.subTaskRoutes.isEmpty()).isTrue()
        }

    @Test
    fun deletingASubTaskOnlineCallsTheDeleteRoute() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()

            val result = repo.deleteSubTask("s1")

            assertThat(result is Result.Success).isTrue()
            assertThat(remote.deletedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(remote.subTaskRoutes).isEqualTo(listOf("p1/t1"))
        }

    @Test
    fun aQueuedDeleteCarriesTheTaskIdBecauseTheRowIsGoneByDrainTime() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            remote.deleteFailWith = DataError.Remote.SERVER_ERROR

            repo.deleteSubTask("s1")

            val op = queue.all().single()
            assertThat(op.operationType).isEqualTo(PendingSyncOperation.OP_DELETE)
            assertThat(op.parentEntityId).isEqualTo("t1")
        }

    @Test
    fun theDrainPushesQueuedSubTasksAndClearsThem() =
        runTest {
            fixture.seedProjectWithTask()
            remote.postFailWith = DataError.Remote.NO_INTERNET
            repo.upsertSubTask(newSubTask())
            remote.postFailWith = null

            repo.syncPendingSubTasks()

            assertThat(remote.postedSubTaskIds).isEqualTo(listOf("s1"))
            assertThat(queue.all().isEmpty()).isTrue()
        }

    @Test
    fun aQueuedDeleteWhoseTaskIsAlreadyGoneIsDroppedNotRetriedForever() =
        runTest {
            fixture.seedProjectWithTaskAndSubTask()
            remote.deleteFailWith = DataError.Remote.SERVER_ERROR
            repo.deleteSubTask("s1")
            remote.deleteFailWith = null
            // The task went too. Deleting a task cascades to its subtasks server-side, so this op has
            // nothing left to do — and the project id it needs for the route is unrecoverable.
            fixture.db.cascadeDeleteTask("t1")

            repo.syncPendingSubTasks()

            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.deletedSubTaskIds.isEmpty()).isTrue()
        }
}
