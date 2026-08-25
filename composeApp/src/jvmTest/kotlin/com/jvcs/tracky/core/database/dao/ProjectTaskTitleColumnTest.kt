package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the split between a task's title and its description on a real (in-memory) database.
 *
 * `project_tasks` spent its whole life as `project_records` with a single `description` column that
 * did both jobs: the write mapper put the title into it and the read mappers took the title back
 * out. A description pulled from the server — the wire has carried both fields since API 1.6.0 —
 * therefore survived only until the next local write, and nothing in the app reported that as
 * anything worse than text quietly going missing.
 *
 * These go through Room rather than raw SQL on purpose: they are what proves the entity, the
 * generated schema and the DAO's hand-written UPDATE all agree on which column is which.
 */
class ProjectTaskTitleColumnTest {

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

    private suspend fun seedProject() {
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
    }

    private fun task(title: String, description: String?) = ProjectTaskEntity(
        projectTaskId = "t1",
        parentProjectId = "p1",
        title = title,
        description = description,
        durationMillis = 0,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = null,
        isFinished = false,
        isTimerRunning = false,
        updatedAtEpochMs = null,
    )

    @Test
    fun aTaskKeepsItsTitleAndDescriptionApart() = runBlocking {
        seedProject()

        dao.upsertProjectTask(task(title = "Write the report", description = "Quarterly, due Friday"))

        val stored = dao.getTaskById("t1")
        assertEquals("Write the report", stored?.title)
        assertEquals("Quarterly, due Friday", stored?.description)
    }

    @Test
    fun aTaskWithoutADescriptionStoresNullRatherThanACopyOfTheTitle() = runBlocking {
        seedProject()

        dao.upsertProjectTask(task(title = "Write the report", description = null))

        assertEquals("Write the report", dao.getTaskById("t1")?.title)
        assertNull(dao.getTaskById("t1")?.description)
    }

    @Test
    fun renamingATaskLeavesItsDescriptionAlone() = runBlocking {
        seedProject()
        dao.upsertProjectTask(task(title = "Write the report", description = "Quarterly, due Friday"))

        dao.updateTaskTitle(taskId = "t1", title = "Write the summary")

        val stored = dao.getTaskById("t1")
        assertEquals("Write the summary", stored?.title)
        // The rename used to overwrite this column, because it *was* the title column.
        assertEquals("Quarterly, due Friday", stored?.description)
    }
}
