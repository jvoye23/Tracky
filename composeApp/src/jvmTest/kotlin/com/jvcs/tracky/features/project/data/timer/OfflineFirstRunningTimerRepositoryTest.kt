package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        repository = OfflineFirstRunningTimerRepository(db.projectDao, db.taskIntervalDao, FakeDeviceIdProvider())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seedProjectAndTask() {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1",
                title = "Tracky App Redesign",
                description = null,
                color = 0xFF7DA0B7.toInt(),
                totalDuration = null,
                startDateTimeEpochMs = 0,
                isFinished = false,
                useLightTextColor = true,
                endDateTimeEpochMs = null,
                isArchived = false,
                trashedAtEpochMs = null,
                isPinned = false,
                updatedAtEpochMs = null,
            ),
        )
        db.projectDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "Token refresh",
                description = null,
                durationMillis = 5.minutes.inWholeMilliseconds,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = null,
            ),
        )
    }

    private suspend fun openTaskInterval() {
        db.taskIntervalDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1",
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = taskStartedAt,
                endDateTimeEpochMs = null,
                durationMillis = 0,
            ),
        )
        db.projectDao.updateSessionTimerStatus("t1", true)
    }

    private suspend fun openSubTaskInterval() {
        db.projectDao.upsertProjectSubTask(
            ProjectSubTaskEntity(
                projectSubTaskId = "s1",
                parentProjectTaskId = "t1",
                parentProjectId = "p1",
                title = "Auth endpoints",
                description = null,
                durationMillis = 9.minutes.inWholeMilliseconds,
                isTimerRunning = true,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                updatedAtEpochMs = null,
            ),
        )
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = "si1",
                parentSubTaskId = "s1",
                parentTaskIntervalId = "i1",
                parentProjectId = "p1",
                startDateTimeEpochMs = subTaskStartedAt,
                endDateTimeEpochMs = null,
                durationMillis = 0,
                startedParentTimer = true,
            ),
        )
    }

    /** A finished session on the task, which is what the banked total is summed from. */
    private suspend fun closedTaskInterval(id: String, millis: Long) {
        db.taskIntervalDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = id,
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = millis,
                durationMillis = millis,
            ),
        )
    }

    private suspend fun closedSubTaskInterval(id: String, millis: Long) {
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = id,
                parentSubTaskId = "s1",
                parentTaskIntervalId = "i1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = millis,
                durationMillis = millis,
            ),
        )
    }

    @Test
    fun aTimerThisDeviceStartedIsNotForeign() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()

            assertThat(repository.observeRunningTimer().first()!!.isForeign).isFalse()
        }

    @Test
    fun anIntervalWithNoProvenanceIsNotForeign() =
        runBlocking {
            // Null means "this device". Every row written before the column existed reads that way,
            // and treating them as foreign would make this device's own crashed timers unreclaimable.
            seedProjectAndTask()
            db.taskIntervalDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i1",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = taskStartedAt,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                    startedByDeviceId = null,
                ),
            )

            assertThat(repository.observeRunningTimer().first()!!.isForeign).isFalse()
        }

    @Test
    fun aTimerAnotherDeviceStartedIsForeign() =
        runBlocking {
            seedProjectAndTask()
            db.taskIntervalDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i1",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = taskStartedAt,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                    startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE,
                ),
            )

            val running = repository.observeRunningTimer().first()!!
            // It still ticks, and still shows the right number — both devices subtract the same
            // startedAt. Only what may be done to it changes.
            assertThat(running.isForeign).isTrue()
            assertThat(running.startedAt.toEpochMilliseconds()).isEqualTo(taskStartedAt)
        }

    @Test
    fun nothingIsRunningWhenNoIntervalIsOpen() =
        runBlocking {
            seedProjectAndTask()

            assertThat(repository.observeRunningTimer().first()).isNull()
        }

    @Test
    fun anOpenTaskIntervalIsNamedByItsProjectAndTask() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()

            val running = repository.observeRunningTimer().first()!!

            assertThat(running.project.title).isEqualTo("Tracky App Redesign")
            assertThat(running.task).isEqualTo(TaskRef(id = "t1", title = "Token refresh"))
            assertThat(running.subTask).isNull()
            assertThat(running.startedAt.toEpochMilliseconds()).isEqualTo(taskStartedAt)
        }

    @Test
    fun aTaskLevelTimerBanksTheSumOfItsClosedIntervals() =
        runBlocking {
            seedProjectAndTask()
            closedTaskInterval("done1", 2.minutes.inWholeMilliseconds)
            closedTaskInterval("done2", 3.minutes.inWholeMilliseconds)
            openTaskInterval()

            // Summed, and the open interval contributes nothing — it has not been measured yet.
            assertThat(repository.observeRunningTimer().first()!!.bankedDuration).isEqualTo(5.minutes)
        }

    @Test
    fun theBankedTotalIgnoresAStaleTaskRow() =
        runBlocking {
            // Why the sum exists. project_tasks.durationMillis is maintained by whichever device did
            // the stopping, so a device that just adopted a foreign timer may not have pulled it yet.
            // Reading it would show a number the interval table disagrees with.
            seedProjectAndTask() // seeds the task row with 5 minutes
            closedTaskInterval("done1", 7.minutes.inWholeMilliseconds)
            openTaskInterval()

            assertThat(repository.observeRunningTimer().first()!!.bankedDuration).isEqualTo(7.minutes)
        }

    @Test
    fun aRunningSubTaskWinsOverTheTaskIntervalEnclosingIt() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()
            openSubTaskInterval()
            closedSubTaskInterval("sdone1", 9.minutes.inWholeMilliseconds)

            val running = repository.observeRunningTimer().first()!!

            assertThat(running.subTask).isEqualTo(TaskRef(id = "s1", title = "Auth endpoints"))
            // Still names the task it sits under - the notification shows all three lines.
            assertThat(running.task.title).isEqualTo("Token refresh")
            // Dated and banked from the subtask, which is the timer the user actually started.
            assertThat(running.startedAt.toEpochMilliseconds()).isEqualTo(subTaskStartedAt)
            // The subtask's own closed intervals, not the task's and not the subtask row's total.
            assertThat(running.bankedDuration).isEqualTo(9.minutes)
        }

    @Test
    fun aSubTaskWithoutBankedTimeStartsFromZero() =
        runBlocking {
            // No closed intervals at all: COALESCE turns the null SUM into zero rather than crashing.
            seedProjectAndTask()
            openTaskInterval()
            openSubTaskInterval()

            assertThat(repository.observeRunningTimer().first()!!.bankedDuration).isEqualTo(Duration.ZERO)
        }

    @Test
    fun theProjectColourTravelsWithTheRunningTimer() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()

            val running = repository.observeRunningTimer().first()!!

            assertThat(running.project.colorArgb).isEqualTo(0xFF7DA0B7.toInt())
            assertThat(running.useLightTextColor).isEqualTo(true)
        }

    @Test
    fun aParkedIntervalIsOpenButNotRunning() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()
            db.strandedIntervalDao.upsertStrandedInterval(
                StrandedIntervalEntity(intervalId = "i1", isSubTaskInterval = false, detectedAtEpochMs = 2_000_000L),
            )

            assertThat(repository.observeRunningTimer().first()).isNull()
        }

    @Test
    fun aParkedSubTaskIntervalFallsBackToTheTaskStillRunningAroundIt() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()
            openSubTaskInterval()
            db.strandedIntervalDao.upsertStrandedInterval(
                StrandedIntervalEntity(intervalId = "si1", isSubTaskInterval = true, detectedAtEpochMs = 2_000_000L),
            )

            val running = repository.observeRunningTimer().first()!!

            assertThat(running.subTask).isNull()
            assertThat(running.task.id).isEqualTo("t1")
        }

    @Test
    fun closingTheIntervalStopsTheRunningTimer() =
        runBlocking {
            seedProjectAndTask()
            openTaskInterval()
            db.taskIntervalDao.upsertTaskInterval(
                db.taskIntervalDao.getIntervalById("i1")!!.copy(endDateTimeEpochMs = 2_000_000L),
            )

            assertThat(repository.observeRunningTimer().first()).isNull()
        }
}
