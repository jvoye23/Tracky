package com.jvcs.tracky.features.project.data.subtask

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The subtask-start invariant, against a real (in-memory) database.
 *
 * `sub_task_intervals.parentTaskIntervalId` is NOT NULL, so a subtask interval cannot exist without
 * a task interval to sit in. That makes starting a subtask a multi-row write — and every rule about
 * which rows it touches lives here rather than in the schema.
 */
internal class RoomLocalSubTaskDataSourceStartTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dataSource: RoomLocalSubTaskDataSource
    private val timeProvider = FakeTimeProvider()

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dataSource = RoomLocalSubTaskDataSource(db.projectDao, timeProvider)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(vararg subTaskIds: String) {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1", title = "title", description = null, color = null,
                totalDuration = null, startDateTimeEpochMs = 0, isFinished = false,
                useLightTextColor = false, endDateTimeEpochMs = null, isArchived = false,
                trashedAtEpochMs = null, isPinned = false, updatedAtEpochMs = null
            )
        )
        db.projectDao.upsertProjectRecord(
            ProjectTaskEntity(
                recordId = "t1", parentProjectId = "p1", description = "task", durationMillis = 0,
                startDateTimeEpochMs = 0, endDateTimeEpochMs = null, isFinished = false,
                isTimerRunning = false, updatedAtEpochMs = null
            )
        )
        subTaskIds.forEach { id ->
            db.projectDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = id, parentProjectTaskId = "t1", parentProjectId = "p1",
                    title = "sub-$id", description = null, durationMillis = null,
                    isTimerRunning = false, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                    isFinished = false, updatedAtEpochMs = null
                )
            )
        }
    }

    private suspend fun taskIsRunning(): Boolean =
        db.projectDao.getTaskById("t1")!!.isTimerRunning

    private suspend fun subTaskIsRunning(id: String): Boolean =
        db.projectDao.getSubTaskById(id)!!.isTimerRunning

    @Test
    fun startingASubTaskWhoseTaskIsIdleOpensTheTaskTimerToo() = runBlocking {
        seed("s1")

        val result = dataSource.startSubTask("s1")

        assertTrue(result is Result.Success)
        val openTaskInterval = db.projectDao.getOpenIntervalBySessionId("t1")
        assertNotNull(openTaskInterval, "the subtask interval needs a task interval to sit in")
        assertEquals(openTaskInterval.intervalId, result.data.subTaskInterval.parentTaskIntervalId)
        assertTrue(taskIsRunning())
        assertTrue(subTaskIsRunning("s1"))
        // It opened the parent, so stopping it later has to close the parent again.
        assertTrue(result.data.subTaskInterval.startedParentTimer)
        // And it is handed back, because that row syncs and only the caller can push it.
        assertEquals(openTaskInterval.intervalId, result.data.taskInterval?.intervalId)
    }

    @Test
    fun startingASubTaskWhoseTaskIsAlreadyRunningNestsInTheOpenInterval() = runBlocking {
        seed("s1")
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i-manual", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = 0, endDateTimeEpochMs = null, durationMillis = 0
            )
        )
        db.projectDao.updateSessionTimerStatus("t1", true)
        timeProvider.now = Instant.fromEpochMilliseconds(5_000)

        val result = dataSource.startSubTask("s1")

        assertTrue(result is Result.Success)
        assertEquals("i-manual", result.data.subTaskInterval.parentTaskIntervalId)
        // Nothing new to push: that interval is already on its way to the server.
        assertNull(result.data.taskInterval)
        // The task timer was the user's doing, so this subtask must not claim it.
        assertFalse(result.data.subTaskInterval.startedParentTimer)
        assertEquals(1, db.projectDao.getTaskWithSubTasksById("t1").first()!!.intervals.size)
    }

    @Test
    fun startingASecondSubTaskClosesTheFirstOne() = runBlocking {
        seed("s1", "s2")
        dataSource.startSubTask("s1")
        timeProvider.now = Instant.fromEpochMilliseconds(30_000)

        dataSource.startSubTask("s2")

        // s1 is closed at exactly the instant s2 starts, so the two never overlap.
        assertNull(db.projectDao.getOpenSubTaskInterval("s1"))
        assertNotNull(db.projectDao.getOpenSubTaskInterval("s2"))
        assertFalse(subTaskIsRunning("s1"))
        assertTrue(subTaskIsRunning("s2"))
        assertEquals(30_000L, db.projectDao.getSubTaskById("s1")!!.durationMillis)
    }

    @Test
    fun theSecondSubTaskReusesTheTaskIntervalTheFirstOneOpened() = runBlocking {
        seed("s1", "s2")
        val first = dataSource.startSubTask("s1")
        timeProvider.now = Instant.fromEpochMilliseconds(30_000)

        val second = dataSource.startSubTask("s2")

        assertTrue(first is Result.Success && second is Result.Success)
        assertEquals(first.data.subTaskInterval.parentTaskIntervalId, second.data.subTaskInterval.parentTaskIntervalId)
        // Only s1 may claim the parent: if s2 claimed it too, stopping either would stop the task.
        assertTrue(first.data.subTaskInterval.startedParentTimer)
        assertFalse(second.data.subTaskInterval.startedParentTimer)
    }

    @Test
    fun startingAnUnknownSubTaskFailsWithoutTouchingTheTask() = runBlocking {
        seed("s1")

        val result = dataSource.startSubTask("nope")

        assertTrue(result is Result.Error)
        assertNull(db.projectDao.getOpenIntervalBySessionId("t1"))
        assertFalse(taskIsRunning())
    }
}
