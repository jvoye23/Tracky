package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
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
 * Exercises [ProjectDao.upsertServerTree] against a real (in-memory) database.
 *
 * The repository-level tests run against a fake that reimplements the same merge rules, so this is
 * the only place the production transaction itself — the flattening, the per-row decisions and the
 * fact that nothing gets deleted — is actually executed.
 */
class ProjectDaoPullMergeTest {

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
    fun tearDown() {
        db.close()
    }

    private fun projectEntity(id: String, updatedAt: Long?) = ProjectEntity(
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
        updatedAtEpochMs = updatedAt,
        sortIndex = null,
    )

    private fun taskEntity(id: String, projectId: String, title: String, updatedAt: Long?) = ProjectTaskEntity(
        recordId = id,
        parentProjectId = projectId,
        description = title,
        durationMillis = 0,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = null,
        isFinished = false,
        isTimerRunning = false,
        updatedAtEpochMs = updatedAt,
    )

    private fun intervalEntity(id: String, taskId: String, end: Long?) = TaskIntervalEntity(
        intervalId = id,
        parentTaskId = taskId,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = end,
        durationMillis = end ?: 0L,
    )

    @Test
    fun writesTheWholeTree() = runBlocking {
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        assertNotNull(dao.getProjectById("p1"))
        assertNotNull(dao.getTaskById("t1"))
        // Intervals are the whole point: this is what a fresh install could not recover before.
        assertEquals(60_000L, dao.getIntervalById("i1")?.durationMillis)
    }

    /** project_records has a CASCADE foreign key onto projects, so the parent must exist first. */
    private suspend fun seedProject(id: String = "p1") {
        dao.upsertProject(projectEntity(id, updatedAt = 0))
    }

    @Test
    fun keepsALocalTaskThatIsNewerThanTheServer() = runBlocking {
        seedProject()
        dao.upsertProjectRecord(taskEntity("t1", "p1", "edited offline", updatedAt = 500))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = listOf(taskEntity("t1", "p1", "stale server copy", updatedAt = 100)),
            intervals = emptyList(),
        )

        assertEquals("edited offline", dao.getTaskById("t1")?.description)
    }

    @Test
    fun takesTheServerTaskWhenItIsNewer() = runBlocking {
        seedProject()
        dao.upsertProjectRecord(taskEntity("t1", "p1", "old local copy", updatedAt = 100))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = listOf(taskEntity("t1", "p1", "fresh from server", updatedAt = 500)),
            intervals = emptyList(),
        )

        assertEquals("fresh from server", dao.getTaskById("t1")?.description)
    }

    @Test
    fun doesNotCloseAnIntervalThatIsStillRunningLocally() = runBlocking {
        dao.upsertTaskInterval(intervalEntity("i1", "t1", end = null)) // timer running here

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        assertNull(dao.getIntervalById("i1")?.endDateTimeEpochMs)
    }

    @Test
    fun leavesRowsTheServerDoesNotKnowAbout() = runBlocking {
        seedProject()
        dao.upsertProjectRecord(taskEntity("local-only", "p1", "created offline", updatedAt = null))
        dao.upsertTaskInterval(intervalEntity("i-local", "local-only", end = 1_000))

        dao.upsertServerTree(projects = emptyList(), tasks = emptyList(), intervals = emptyList())

        assertNotNull(dao.getTaskById("local-only"))
        assertNotNull(dao.getIntervalById("i-local"))
        Unit
    }
}
