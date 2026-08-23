package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
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

    private fun intervalEntity(
        id: String,
        taskId: String,
        end: Long?,
        projectId: String = "p1",
    ) = TaskIntervalEntity(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = projectId,
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

    /** task_intervals cascades from both project_records and projects, so seed the whole chain. */
    private suspend fun seedTask(taskId: String = "t1", projectId: String = "p1") {
        seedProject(projectId)
        dao.upsertProjectRecord(taskEntity(taskId, projectId, "seeded", updatedAt = 0))
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
        seedTask()
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

    private fun subTaskEntity(
        id: String,
        taskId: String,
        title: String,
        updatedAt: Long?,
        projectId: String = "p1",
    ) = ProjectSubTaskEntity(
        projectSubTaskId = id,
        parentProjectTaskId = taskId,
        parentProjectId = projectId,
        title = title,
        description = null,
        durationMillis = 0,
        isTimerRunning = false,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = null,
        isFinished = false,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun writesSubTasksNestedTwoLevelsDown() = runBlocking {
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "from server", updatedAt = 100)),
        )

        assertEquals("from server", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun keepsALocalSubTaskThatIsNewerThanTheServer() = runBlocking {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("s1", "t1", "edited offline", updatedAt = 500))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "stale server copy", updatedAt = 100)),
        )

        assertEquals("edited offline", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun takesTheServerSubTaskWhenItIsNewer() = runBlocking {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("s1", "t1", "old local copy", updatedAt = 100))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "renamed elsewhere", updatedAt = 900)),
        )

        assertEquals("renamed elsewhere", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun neverDeletesALocalOnlySubTask() = runBlocking<Unit> {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("local-only", "t1", "created offline", updatedAt = null))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = emptyList(),
        )

        // Still queued for upload — a pull must never delete it.
        assertNotNull(dao.getSubTaskById("local-only"))
    }

    @Test
    fun skipsAnOrphanSubTaskInsteadOfLosingTheWholePull() = runBlocking<Unit> {
        // project_sub_tasks has a CASCADE foreign key onto project_records. Without the filter this
        // row throws inside the transaction and takes the project and task rows down with it — the
        // whole pull, not just the bad row.
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = emptyList(),
            subTasks = listOf(
                subTaskEntity("orphan", "missing-task", "no parent here", updatedAt = 100),
                subTaskEntity("s1", "t1", "fine", updatedAt = 100),
            ),
        )

        assertNull(dao.getSubTaskById("orphan"))
        // Everything around it survived.
        assertNotNull(dao.getProjectById("p1"))
        assertNotNull(dao.getTaskById("t1"))
        assertNotNull(dao.getSubTaskById("s1"))
    }
}
