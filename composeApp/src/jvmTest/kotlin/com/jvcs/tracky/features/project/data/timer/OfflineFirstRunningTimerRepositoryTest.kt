package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What "currently running" means: one open, unparked interval, named all the way up to its project.
 *
 * The awkward case throughout is a subtask, because timing one leaves *two* intervals open - the
 * subtask's and the task interval enclosing it - and only one timer is running.
 */
internal class OfflineFirstRunningTimerRepositoryTest {

    private lateinit var db: TrackyDatabase
    private lateinit var repository: OfflineFirstRunningTimerRepository

    private val taskStartedAt = 1_000_000L
    private val subTaskStartedAt = 1_200_000L

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        repository = OfflineFirstRunningTimerRepository(projectDao = db.projectDao)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seedProjectAndTask() {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1", title = "Tracky App Redesign", description = null,
                color = 0xFF7DA0B7.toInt(), totalDuration = null, startDateTimeEpochMs = 0,
                isFinished = false, useLightTextColor = true, endDateTimeEpochMs = null,
                isArchived = false, trashedAtEpochMs = null, isPinned = false, updatedAtEpochMs = null
            )
        )
        db.projectDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1", parentProjectId = "p1", title = "Token refresh",
                description = null, durationMillis = 5.minutes.inWholeMilliseconds,
                startDateTimeEpochMs = 0, endDateTimeEpochMs = null, isFinished = false,
                isTimerRunning = false, updatedAtEpochMs = null
            )
        )
    }

    private suspend fun openTaskInterval() {
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = taskStartedAt, endDateTimeEpochMs = null, durationMillis = 0
            )
        )
        db.projectDao.updateSessionTimerStatus("t1", true)
    }

    private suspend fun openSubTaskInterval() {
        db.projectDao.upsertProjectSubTask(
            ProjectSubTaskEntity(
                projectSubTaskId = "s1", parentProjectTaskId = "t1", parentProjectId = "p1",
                title = "Auth endpoints", description = null,
                durationMillis = 9.minutes.inWholeMilliseconds, isTimerRunning = true,
                startDateTimeEpochMs = 0, endDateTimeEpochMs = null, isFinished = false,
                updatedAtEpochMs = null
            )
        )
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = "si1", parentSubTaskId = "s1", parentTaskIntervalId = "i1",
                parentProjectId = "p1", startDateTimeEpochMs = subTaskStartedAt,
                endDateTimeEpochMs = null, durationMillis = 0, startedParentTimer = true
            )
        )
    }

    @Test
    fun nothingIsRunningWhenNoIntervalIsOpen() = runBlocking {
        seedProjectAndTask()

        assertNull(repository.observeRunningTimer().first())
    }

    @Test
    fun anOpenTaskIntervalIsNamedByItsProjectAndTask() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()

        val running = repository.observeRunningTimer().first()!!

        assertEquals("Tracky App Redesign", running.projectTitle)
        assertEquals("Token refresh", running.taskTitle)
        assertEquals("t1", running.taskId)
        assertNull(running.subTaskId)
        assertNull(running.subTaskTitle)
        assertEquals(taskStartedAt, running.startedAt.toEpochMilliseconds())
    }

    @Test
    fun aTaskLevelTimerBanksFromTheTask() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()

        assertEquals(5.minutes, repository.observeRunningTimer().first()!!.bankedDuration)
    }

    @Test
    fun aRunningSubTaskWinsOverTheTaskIntervalEnclosingIt() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        openSubTaskInterval()

        val running = repository.observeRunningTimer().first()!!

        assertEquals("s1", running.subTaskId)
        assertEquals("Auth endpoints", running.subTaskTitle)
        // Still names the task it sits under - the notification shows all three lines.
        assertEquals("Token refresh", running.taskTitle)
        // Dated and banked from the subtask, which is the timer the user actually started.
        assertEquals(subTaskStartedAt, running.startedAt.toEpochMilliseconds())
        assertEquals(9.minutes, running.bankedDuration)
    }

    @Test
    fun aSubTaskWithoutBankedTimeStartsFromZero() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        openSubTaskInterval()
        db.projectDao.upsertProjectSubTask(
            db.projectDao.getSubTaskById("s1")!!.copy(durationMillis = null)
        )

        assertEquals(Duration.ZERO, repository.observeRunningTimer().first()!!.bankedDuration)
    }

    @Test
    fun theProjectColourTravelsWithTheRunningTimer() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()

        val running = repository.observeRunningTimer().first()!!

        assertEquals(0xFF7DA0B7.toInt(), running.projectColorArgb)
        assertEquals(true, running.useLightTextColor)
    }

    @Test
    fun aParkedIntervalIsOpenButNotRunning() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        db.projectDao.upsertStrandedInterval(
            StrandedIntervalEntity(intervalId = "i1", isSubTaskInterval = false, detectedAtEpochMs = 2_000_000L)
        )

        assertNull(repository.observeRunningTimer().first())
    }

    @Test
    fun aParkedSubTaskIntervalFallsBackToTheTaskStillRunningAroundIt() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        openSubTaskInterval()
        db.projectDao.upsertStrandedInterval(
            StrandedIntervalEntity(intervalId = "si1", isSubTaskInterval = true, detectedAtEpochMs = 2_000_000L)
        )

        val running = repository.observeRunningTimer().first()!!

        assertNull(running.subTaskId)
        assertEquals("t1", running.taskId)
    }

    @Test
    fun closingTheIntervalStopsTheRunningTimer() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        db.projectDao.upsertTaskInterval(
            db.projectDao.getIntervalById("i1")!!.copy(endDateTimeEpochMs = 2_000_000L)
        )

        assertNull(repository.observeRunningTimer().first())
    }
}
