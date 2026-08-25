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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pins down the cascading deletes on a real (in-memory) database.
 *
 * Deleting a project used to leave every interval underneath it in the table forever: only
 * `project_tasks` cascaded, and `task_intervals` had no foreign key at all. The delete paths in
 * the data source are now plain single-table deletes that rely entirely on the schema, so this is
 * what proves the cleanup actually happens — and that Room really does enable foreign key
 * enforcement at runtime.
 */
class ProjectDaoCascadeTest {

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

    private suspend fun seedTree() {
        dao.upsertProject(
            ProjectEntity(
                projectId = "p1",
                title = "title",
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
                sortIndex = null,
            )
        )
        listOf("t1", "t2").forEach { taskId ->
            dao.upsertProjectTask(
                ProjectTaskEntity(
                    projectTaskId = taskId,
                    parentProjectId = "p1",
                    title = "task-$taskId",
                    description = null,
                    durationMillis = 0,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    isTimerRunning = false,
                    updatedAtEpochMs = null,
                )
            )
            dao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i-$taskId",
                    parentTaskId = taskId,
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = 60_000,
                    durationMillis = 60_000,
                )
            )
        }
    }

    @Test
    fun deletingAProjectAlsoDeletesItsTasksAndIntervals() = runBlocking {
        seedTree()

        dao.deleteProject("p1")

        assertNull(dao.getTaskById("t1"))
        assertNull(dao.getTaskById("t2"))
        assertNull(dao.getIntervalById("i-t1"))
        assertNull(dao.getIntervalById("i-t2"))
    }

    @Test
    fun deletingASingleTaskDeletesOnlyItsOwnIntervals() = runBlocking {
        seedTree()

        dao.deleteProjectTask("t1")

        assertNull(dao.getIntervalById("i-t1"))
        // The sibling task is untouched, so its tracked time has to survive.
        assertNotNull(dao.getTaskById("t2"))
        assertNotNull(dao.getIntervalById("i-t2"))
        Unit
    }

    @Test
    fun deletingAllProjectsClearsTheWholeTree() = runBlocking {
        seedTree()

        dao.deleteAllProjects()

        assertNull(dao.getTaskById("t1"))
        assertNull(dao.getIntervalById("i-t1"))
        assertNull(dao.getIntervalById("i-t2"))
    }
}
