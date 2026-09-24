package com.jvcs.tracky.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises [ServerTreeWriter.applyDelta] — specifically the deletions, which are the first thing in
 * this codebase that lets a pull remove local data.
 *
 * `upsertServerTree` promises never to delete, because in a full-tree pull an absent row is
 * ambiguous. A tombstone is not ambiguous. The line between the two is what these tests hold.
 */
class ServerTreeWriterApplyDeltaTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dao: ProjectDao
    private lateinit var writer: ServerTreeWriter

    @BeforeTest
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dao = db.projectDao
        writer = ServerTreeWriter(db)
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun projectEntity(id: String) =
        ProjectEntity(
            projectId = id,
            title = "title-$id",
            description = null,
            color = null,
            totalDuration = null,
            startDateTimeEpochMs = 0,
            isFinished = false,
            useLightTextColor = false,
            endDateTimeEpochMs = null,
            isArchived = false,
            trashedAtEpochMs = null,
            isPinned = false,
            updatedAtEpochMs = null,
        )

    private fun taskEntity(id: String, projectId: String = "p1") =
        ProjectTaskEntity(
            projectTaskId = id,
            parentProjectId = projectId,
            title = "task-$id",
            description = null,
            durationMillis = 0,
            startDateTimeEpochMs = 0,
            endDateTimeEpochMs = null,
            isFinished = false,
            isTimerRunning = false,
            updatedAtEpochMs = null,
        )

    private fun intervalEntity(id: String, taskId: String = "t1") =
        TaskIntervalEntity(
            intervalId = id,
            parentTaskId = taskId,
            parentProjectId = "p1",
            startDateTimeEpochMs = 0,
            endDateTimeEpochMs = 1_000,
            durationMillis = 1_000,
        )

    private suspend fun seedTree() {
        dao.upsertProject(projectEntity("p1"))
        db.taskDao.upsertProjectTask(taskEntity("t1"))
        db.taskIntervalDao.upsertTaskInterval(intervalEntity("i1"))
    }

    private suspend fun queuePush(entityId: String, entityType: String) {
        db.pendingSyncDao.enqueueDeduped(
            PendingSyncEntity(
                operationId = "op-$entityId",
                entityId = entityId,
                entityType = entityType,
                operationType = "UPDATE",
                createdAtEpochMs = 0,
                parentEntityId = null,
            ),
        )
    }

    private suspend fun applyDeletions(
        projects: List<String> = emptyList(),
        tasks: List<String> = emptyList(),
        intervals: List<String> = emptyList(),
    ) = writer.applyDelta(
        upserts = ServerTreeRows(),
        deletions = ServerTombstones(projectIds = projects, taskIds = tasks, intervalIds = intervals),
    )

    @Test
    fun aTombstoneDeletesTheRow() =
        runBlocking {
            seedTree()

            applyDeletions(tasks = listOf("t1"))

            // The whole point of the feed: a deletion on another device finally lands here.
            assertThat(db.taskDao.getTaskById("t1")).isNull()
        }

    @Test
    fun deletingAProjectTakesItsSubtreeWithIt() =
        runBlocking {
            seedTree()

            applyDeletions(projects = listOf("p1"))

            assertThat(dao.getProjectById("p1")).isNull()
            assertThat(db.taskDao.getTaskById("t1")).isNull()
            assertThat(db.taskIntervalDao.getIntervalById("i1")).isNull()
        }

    @Test
    fun aTombstoneForARowWithAQueuedChangeIsIgnored() =
        runBlocking {
            seedTree()
            queuePush("t1", "project_task")

            applyDeletions(tasks = listOf("t1"))

            // The server emitted this before it heard about the edit this device is still carrying.
            // Honouring it would destroy work the outbox has not delivered yet.
            assertThat(db.taskDao.getTaskById("t1")?.projectTaskId).isEqualTo("t1")
        }

    @Test
    fun aQueuedChangeOnOneRowDoesNotShieldAnother() =
        runBlocking {
            seedTree()
            db.taskDao.upsertProjectTask(taskEntity("t2"))
            queuePush("t1", "project_task")

            applyDeletions(tasks = listOf("t1", "t2"))

            assertThat(db.taskDao.getTaskById("t1")).isNotNull()
            assertThat(db.taskDao.getTaskById("t2")).isNull()
        }

    @Test
    fun anUnknownTombstoneIdIsANoOp() =
        runBlocking {
            seedTree()

            applyDeletions(tasks = listOf("never-existed"))

            assertThat(db.taskDao.getTaskById("t1")?.projectTaskId).isEqualTo("t1")
        }

    @Test
    fun aChildTombstoneArrivingWithItsParentsIsHarmless() =
        runBlocking {
            seedTree()

            // The project's cascade already removed the task by the time its own tombstone runs.
            applyDeletions(projects = listOf("p1"), tasks = listOf("t1"), intervals = listOf("i1"))

            assertThat(dao.getProjectById("p1")).isNull()
        }

    @Test
    fun upsertsAndDeletionsLandInTheSamePass() =
        runBlocking {
            seedTree()

            writer.applyDelta(
                upserts = ServerTreeRows(projects = listOf(projectEntity("p2"))),
                deletions = ServerTombstones(projectIds = listOf("p1")),
            )

            assertThat(dao.getProjectById("p2")).isNotNull()
            assertThat(dao.getProjectById("p1")).isNull()
        }

    @Test
    fun aDeletedIntervalIsRemovedWithoutTouchingItsTask() =
        runBlocking {
            seedTree()

            applyDeletions(intervals = listOf("i1"))

            assertThat(db.taskIntervalDao.getIntervalById("i1")).isNull()
            assertThat(db.taskDao.getTaskById("t1")?.projectTaskId).isEqualTo("t1")
        }
}
