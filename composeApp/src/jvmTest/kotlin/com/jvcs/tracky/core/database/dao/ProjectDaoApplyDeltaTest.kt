package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Exercises [ProjectDao.applyDelta] — specifically the deletions, which are the first thing in
 * this codebase that lets a pull remove local data.
 *
 * `upsertServerTree` promises never to delete, because in a full-tree pull an absent row is
 * ambiguous. A tombstone is not ambiguous. The line between the two is what these tests hold.
 */
class ProjectDaoApplyDeltaTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dao: ProjectDao

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.projectDao
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun projectEntity(id: String) = ProjectEntity(
        projectId = id, title = "title-$id", description = null, color = null,
        totalDuration = null, startDateTimeEpochMs = 0, isFinished = false,
        useLightTextColor = false, endDateTimeEpochMs = null, isArchived = false,
        trashedAtEpochMs = null, isPinned = false, updatedAtEpochMs = null
    )

    private fun taskEntity(id: String, projectId: String = "p1") = ProjectTaskEntity(
        projectTaskId = id, parentProjectId = projectId, title = "task-$id", description = null,
        durationMillis = 0, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
        isFinished = false, isTimerRunning = false, updatedAtEpochMs = null
    )

    private fun intervalEntity(id: String, taskId: String = "t1") = TaskIntervalEntity(
        intervalId = id, parentTaskId = taskId, parentProjectId = "p1",
        startDateTimeEpochMs = 0, endDateTimeEpochMs = 1_000, durationMillis = 1_000
    )

    private suspend fun seedTree() {
        dao.upsertProject(projectEntity("p1"))
        dao.upsertProjectTask(taskEntity("t1"))
        dao.upsertTaskInterval(intervalEntity("i1"))
    }

    private suspend fun queuePush(entityId: String, entityType: String) {
        db.pendingSyncDao.enqueueDeduped(
            PendingSyncEntity(
                operationId = "op-$entityId", entityId = entityId, entityType = entityType,
                operationType = "UPDATE", createdAtEpochMs = 0, parentEntityId = null
            )
        )
    }

    private suspend fun applyDeletions(
        projects: List<String> = emptyList(),
        tasks: List<String> = emptyList(),
        intervals: List<String> = emptyList()
    ) = dao.applyDelta(
        projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
        subTasks = emptyList(), subTaskIntervals = emptyList(),
        deletedProjectIds = projects, deletedTaskIds = tasks, deletedIntervalIds = intervals,
        deletedSubTaskIds = emptyList(), deletedSubTaskIntervalIds = emptyList()
    )

    @Test
    fun aTombstoneDeletesTheRow() = runBlocking {
        seedTree()

        applyDeletions(tasks = listOf("t1"))

        // The whole point of the feed: a deletion on another device finally lands here.
        assertNull(dao.getTaskById("t1"))
    }

    @Test
    fun deletingAProjectTakesItsSubtreeWithIt() = runBlocking {
        seedTree()

        applyDeletions(projects = listOf("p1"))

        assertNull(dao.getProjectById("p1"))
        assertNull(dao.getTaskById("t1"))
        assertNull(dao.getIntervalById("i1"))
    }

    @Test
    fun aTombstoneForARowWithAQueuedChangeIsIgnored() = runBlocking {
        seedTree()
        queuePush("t1", "project_task")

        applyDeletions(tasks = listOf("t1"))

        // The server emitted this before it heard about the edit this device is still carrying.
        // Honouring it would destroy work the outbox has not delivered yet.
        assertEquals("t1", dao.getTaskById("t1")?.projectTaskId)
    }

    @Test
    fun aQueuedChangeOnOneRowDoesNotShieldAnother() = runBlocking {
        seedTree()
        dao.upsertProjectTask(taskEntity("t2"))
        queuePush("t1", "project_task")

        applyDeletions(tasks = listOf("t1", "t2"))

        assertNotNull(dao.getTaskById("t1"))
        assertNull(dao.getTaskById("t2"))
    }

    @Test
    fun anUnknownTombstoneIdIsANoOp() = runBlocking {
        seedTree()

        applyDeletions(tasks = listOf("never-existed"))

        assertEquals("t1", dao.getTaskById("t1")?.projectTaskId)
    }

    @Test
    fun aChildTombstoneArrivingWithItsParentsIsHarmless() = runBlocking {
        seedTree()

        // The project's cascade already removed the task by the time its own tombstone runs.
        applyDeletions(projects = listOf("p1"), tasks = listOf("t1"), intervals = listOf("i1"))

        assertNull(dao.getProjectById("p1"))
    }

    @Test
    fun upsertsAndDeletionsLandInTheSamePass() = runBlocking {
        seedTree()

        dao.applyDelta(
            projects = listOf(projectEntity("p2")),
            tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(), subTaskIntervals = emptyList(),
            deletedProjectIds = listOf("p1"), deletedTaskIds = emptyList(),
            deletedIntervalIds = emptyList(), deletedSubTaskIds = emptyList(),
            deletedSubTaskIntervalIds = emptyList()
        )

        assertNotNull(dao.getProjectById("p2"))
        assertNull(dao.getProjectById("p1"))
    }

    @Test
    fun aDeletedIntervalIsRemovedWithoutTouchingItsTask() = runBlocking {
        seedTree()

        applyDeletions(intervals = listOf("i1"))

        assertNull(dao.getIntervalById("i1"))
        assertEquals("t1", dao.getTaskById("t1")?.projectTaskId)
    }
}
