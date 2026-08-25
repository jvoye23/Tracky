package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the read shape the project detail screen depends on.
 *
 * The screen used to render no subtasks at all: it read through `getProjectWithTasksById`, whose
 * relation stops at `ProjectTaskEntity`, so every task reached the UI with `subTasks == null` and
 * the mapper silently flattened that to an empty list. `getProjectWithTaskTreeById` is the query
 * that actually reaches the subtasks, so this is what proves the tree comes back hydrated.
 */
class ProjectDaoTaskTreeTest {

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
                title = "project",
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
        dao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "task",
                description = null,
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = null,
            )
        )
        // A second task with no subtasks: the relation must not spill s1/s2 onto it.
        dao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t2",
                parentProjectId = "p1",
                title = "task-without-subtasks",
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
                intervalId = "ti1",
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = 5_000,
                durationMillis = 5_000,
            )
        )
        listOf("s1", "s2").forEach { subTaskId ->
            dao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = subTaskId,
                    parentProjectTaskId = "t1",
                    parentProjectId = "p1",
                    title = "subtask-$subTaskId",
                    description = null,
                    durationMillis = 3_000,
                    isTimerRunning = false,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    updatedAtEpochMs = null,
                )
            )
        }
        dao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = "si1",
                parentSubTaskId = "s1",
                parentTaskIntervalId = "ti1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = 3_000,
                durationMillis = 3_000,
            )
        )
    }

    @Test
    fun `getProjectWithTaskTreeById returns tasks hydrated with their subtasks`() = runBlocking {
        seedTree()

        val tree = assertNotNull(dao.getProjectWithTaskTreeById("p1"))

        assertEquals("p1", tree.project.projectId)
        assertEquals(2, tree.projectTasks.size)

        val task = assertNotNull(tree.projectTasks.find { it.task.projectTaskId == "t1" })
        assertEquals(
            listOf("s1", "s2"),
            task.subTasks.map { it.subTask.projectSubTaskId }.sorted()
        )
        assertEquals(listOf("ti1"), task.intervals.map { it.intervalId })

        val subTaskWithIntervals = assertNotNull(
            task.subTasks.find { it.subTask.projectSubTaskId == "s1" }
        )
        assertEquals(listOf("si1"), subTaskWithIntervals.intervals.map { it.subTaskIntervalId })
        assertTrue(subTaskWithIntervals.intervals.single().durationMillis > 0)
    }

    @Test
    fun `getProjectWithTaskTreeById leaves a task without subtasks empty`() = runBlocking {
        seedTree()

        val tree = assertNotNull(dao.getProjectWithTaskTreeById("p1"))
        val task = assertNotNull(tree.projectTasks.find { it.task.projectTaskId == "t2" })

        assertTrue(task.subTasks.isEmpty())
        assertTrue(task.intervals.isEmpty())
    }
}
