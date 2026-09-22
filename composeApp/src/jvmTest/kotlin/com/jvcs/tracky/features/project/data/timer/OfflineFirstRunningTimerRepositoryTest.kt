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
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
        repository = OfflineFirstRunningTimerRepository(db.projectDao, FakeDeviceIdProvider())
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

    /** A finished session on the task, which is what the banked total is summed from. */
    private suspend fun closedTaskInterval(id: String, millis: Long) {
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = id, parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = 0, endDateTimeEpochMs = millis, durationMillis = millis
            )
        )
    }

    private suspend fun closedSubTaskInterval(id: String, millis: Long) {
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = id, parentSubTaskId = "s1", parentTaskIntervalId = "i1",
                parentProjectId = "p1", startDateTimeEpochMs = 0,
                endDateTimeEpochMs = millis, durationMillis = millis
            )
        )
    }

    @Test
    fun aTimerThisDeviceStartedIsNotForeign() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()

        assertFalse(repository.observeRunningTimer().first()!!.isForeign)
    }

    @Test
    fun anIntervalWithNoProvenanceIsNotForeign() = runBlocking {
        // Null means "this device". Every row written before the column existed reads that way,
        // and treating them as foreign would make this device's own crashed timers unreclaimable.
        seedProjectAndTask()
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = taskStartedAt, endDateTimeEpochMs = null,
                durationMillis = 0, startedByDeviceId = null
            )
        )

        assertFalse(repository.observeRunningTimer().first()!!.isForeign)
    }

    @Test
    fun aTimerAnotherDeviceStartedIsForeign() = runBlocking {
        seedProjectAndTask()
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = taskStartedAt, endDateTimeEpochMs = null,
                durationMillis = 0, startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE
            )
        )

        val running = repository.observeRunningTimer().first()!!
        // It still ticks, and still shows the right number — both devices subtract the same
        // startedAt. Only what may be done to it changes.
        assertTrue(running.isForeign)
        assertEquals(taskStartedAt, running.startedAt.toEpochMilliseconds())
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

        assertEquals("Tracky App Redesign", running.project.title)
        assertEquals(TaskRef(id = "t1", title = "Token refresh"), running.task)
        assertNull(running.subTask)
        assertEquals(taskStartedAt, running.startedAt.toEpochMilliseconds())
    }

    @Test
    fun aTaskLevelTimerBanksTheSumOfItsClosedIntervals() = runBlocking {
        seedProjectAndTask()
        closedTaskInterval("done1", 2.minutes.inWholeMilliseconds)
        closedTaskInterval("done2", 3.minutes.inWholeMilliseconds)
        openTaskInterval()

        // Summed, and the open interval contributes nothing — it has not been measured yet.
        assertEquals(5.minutes, repository.observeRunningTimer().first()!!.bankedDuration)
    }

    @Test
    fun theBankedTotalIgnoresAStaleTaskRow() = runBlocking {
        // Why the sum exists. project_tasks.durationMillis is maintained by whichever device did
        // the stopping, so a device that just adopted a foreign timer may not have pulled it yet.
        // Reading it would show a number the interval table disagrees with.
        seedProjectAndTask() // seeds the task row with 5 minutes
        closedTaskInterval("done1", 7.minutes.inWholeMilliseconds)
        openTaskInterval()

        assertEquals(7.minutes, repository.observeRunningTimer().first()!!.bankedDuration)
    }

    @Test
    fun aRunningSubTaskWinsOverTheTaskIntervalEnclosingIt() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()
        openSubTaskInterval()
        closedSubTaskInterval("sdone1", 9.minutes.inWholeMilliseconds)

        val running = repository.observeRunningTimer().first()!!

        assertEquals(TaskRef(id = "s1", title = "Auth endpoints"), running.subTask)
        // Still names the task it sits under - the notification shows all three lines.
        assertEquals("Token refresh", running.task.title)
        // Dated and banked from the subtask, which is the timer the user actually started.
        assertEquals(subTaskStartedAt, running.startedAt.toEpochMilliseconds())
        // The subtask's own closed intervals, not the task's and not the subtask row's total.
        assertEquals(9.minutes, running.bankedDuration)
    }

    @Test
    fun aSubTaskWithoutBankedTimeStartsFromZero() = runBlocking {
        // No closed intervals at all: COALESCE turns the null SUM into zero rather than crashing.
        seedProjectAndTask()
        openTaskInterval()
        openSubTaskInterval()

        assertEquals(Duration.ZERO, repository.observeRunningTimer().first()!!.bankedDuration)
    }

    @Test
    fun theProjectColourTravelsWithTheRunningTimer() = runBlocking {
        seedProjectAndTask()
        openTaskInterval()

        val running = repository.observeRunningTimer().first()!!

        assertEquals(0xFF7DA0B7.toInt(), running.project.colorArgb)
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

        assertNull(running.subTask)
        assertEquals("t1", running.task.id)
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
