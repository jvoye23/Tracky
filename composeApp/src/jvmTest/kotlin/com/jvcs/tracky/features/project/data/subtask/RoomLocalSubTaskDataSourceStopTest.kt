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
import com.jvcs.tracky.features.project.data.task.RoomLocalTaskDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The subtask-stop invariant, against a real (in-memory) database.
 *
 * Stopping crosses the two levels in both directions: a subtask that started its parent's timer has
 * to stop it again, and a task can never stop while a subtask under it is still running — that would
 * strand an open subtask interval inside a closed task interval, a state the foreign key permits but
 * nothing could reconcile.
 */
internal class RoomLocalSubTaskDataSourceStopTest {

    private lateinit var db: TrackyDatabase
    private lateinit var subTasks: RoomLocalSubTaskDataSource
    private lateinit var tasks: RoomLocalTaskDataSource
    private val timeProvider = FakeTimeProvider()

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        subTasks = RoomLocalSubTaskDataSource(db.projectDao, timeProvider)
        tasks = RoomLocalTaskDataSource(db.projectDao, timeProvider)
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
        db.projectDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1", parentProjectId = "p1", title = "task", description = null,
                durationMillis = 0,
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

    private suspend fun taskIsRunning() = db.projectDao.getTaskById("t1")!!.isTimerRunning
    private suspend fun subTaskIsRunning(id: String) = db.projectDao.getSubTaskById(id)!!.isTimerRunning

    @Test
    fun stoppingASubTaskThatStartedTheTaskStopsTheTaskToo() = runBlocking {
        seed("s1")
        subTasks.startSubTask("s1")
        timeProvider.now = Instant.fromEpochMilliseconds(60_000)

        val result = subTasks.stopSubTask("s1")

        assertTrue(result is Result.Success)
        assertNull(db.projectDao.getOpenSubTaskInterval("s1"))
        assertNull(db.projectDao.getOpenIntervalBySessionId("t1"))
        assertFalse(subTaskIsRunning("s1"))
        assertFalse(taskIsRunning())
        assertEquals(60_000L, db.projectDao.getSubTaskById("s1")!!.durationMillis)
        assertEquals(60_000L, db.projectDao.getTaskById("t1")!!.durationMillis)
    }

    @Test
    fun stoppingASubTaskLeavesATaskTheUserStartedRunning() = runBlocking {
        seed("s1")
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity("i-manual", "t1", "p1", 0, null, 0)
        )
        db.projectDao.updateSessionTimerStatus("t1", true)
        subTasks.startSubTask("s1")
        timeProvider.now = Instant.fromEpochMilliseconds(60_000)

        subTasks.stopSubTask("s1")

        assertNull(db.projectDao.getOpenSubTaskInterval("s1"))
        // The task timer was not this subtask's to stop.
        assertNotNull(db.projectDao.getOpenIntervalBySessionId("t1"))
        assertTrue(taskIsRunning())
    }

    @Test
    fun stoppingTheSiblingThatNestedDoesNotStopTheTask() = runBlocking {
        seed("s1", "s2")
        subTasks.startSubTask("s1") // this one opens the task interval
        timeProvider.now = Instant.fromEpochMilliseconds(10_000)
        subTasks.startSubTask("s2") // this one only nests inside it
        timeProvider.now = Instant.fromEpochMilliseconds(30_000)

        subTasks.stopSubTask("s2")

        assertNotNull(db.projectDao.getOpenIntervalBySessionId("t1"))
        assertTrue(taskIsRunning())
    }

    @Test
    fun stoppingASubTaskThatIsNotRunningIsANoOp() = runBlocking {
        seed("s1")

        val result = subTasks.stopSubTask("s1")

        assertTrue(result is Result.Success)
        assertNull(result.data)
        assertFalse(taskIsRunning())
    }

    @Test
    fun stoppingTheTaskClosesTheSubTaskRunningInsideIt() = runBlocking {
        seed("s1")
        subTasks.startSubTask("s1")
        timeProvider.now = Instant.fromEpochMilliseconds(60_000)

        tasks.stopTask("t1")

        // Neither may be left open — and both are banked at the same instant.
        assertNull(db.projectDao.getOpenSubTaskInterval("s1"))
        assertNull(db.projectDao.getOpenIntervalBySessionId("t1"))
        assertFalse(subTaskIsRunning("s1"))
        assertFalse(taskIsRunning())
        assertEquals(60_000L, db.projectDao.getSubTaskById("s1")!!.durationMillis)
        assertEquals(60_000L, db.projectDao.getTaskById("t1")!!.durationMillis)
    }

    @Test
    fun stoppingATaskWithNoRunningSubTaskStillWorks() = runBlocking {
        seed("s1")
        db.projectDao.upsertTaskInterval(TaskIntervalEntity("i1", "t1", "p1", 0, null, 0))
        db.projectDao.updateSessionTimerStatus("t1", true)
        timeProvider.now = Instant.fromEpochMilliseconds(60_000)

        val result = tasks.stopTask("t1")

        assertTrue(result is Result.Success)
        assertEquals(60_000L, result.data?.durationMillis)
        assertFalse(taskIsRunning())
    }
}
