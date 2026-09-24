package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.testServerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The start-up pass, against a real (in-memory) database.
 *
 * The invariant every test here defends: the pass **banks nothing**. It parks the row and clears
 * the timer flag, and the time stays uncounted until the user says what to do with it. Anything
 * that writes a duration here is the 75-hour day coming back.
 */
internal class StrandedTimerReconcilerTest {

    private lateinit var db: TrackyDatabase
    private lateinit var reconciler: StrandedTimerReconciler
    private val timeProvider = FakeTimeProvider()

    /** Three days after the intervals below start, as if the app had been killed on day one. */
    private val threeDaysLater = Instant.fromEpochMilliseconds(3 * 24 * 60 * 60 * 1_000L)

    @BeforeTest
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        reconciler =
            StrandedTimerReconciler(db.projectDao, testServerClock(timeProvider), FakeDeviceIdProvider(), TestScope())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(withSubTask: Boolean = false) {
        db.projectDao.upsertProject(
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
            ),
        )
        db.projectDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "task",
                description = null,
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = true,
                updatedAtEpochMs = null,
            ),
        )
        if (withSubTask) {
            db.projectDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = "s1",
                    parentProjectTaskId = "t1",
                    parentProjectId = "p1",
                    title = "sub",
                    description = null,
                    durationMillis = null,
                    isTimerRunning = true,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    updatedAtEpochMs = null,
                ),
            )
        }
    }

    private suspend fun openTaskInterval(
        id: String = "i1",
        startedAt: Long = 0,
        startedByDeviceId: String? = FakeDeviceIdProvider.THIS_DEVICE,
    ) {
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = id,
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = startedAt,
                endDateTimeEpochMs = null,
                durationMillis = 0,
                startedByDeviceId = startedByDeviceId,
            ),
        )
    }

    private suspend fun openSubTaskInterval(
        id: String = "si1",
        parentTaskIntervalId: String = "i1",
        startedByDeviceId: String? = FakeDeviceIdProvider.THIS_DEVICE,
    ) {
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = id,
                parentSubTaskId = "s1",
                parentTaskIntervalId = parentTaskIntervalId,
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                durationMillis = 0,
                startedParentTimer = true,
                startedByDeviceId = startedByDeviceId,
            ),
        )
    }

    @Test
    fun anIntervalLeftOpenByAPreviousProcessIsParkedWithoutBankingAnything() =
        runBlocking {
            seed()
            openTaskInterval()
            timeProvider.now = threeDaysLater

            reconciler.reconcile()

            val parked = db.projectDao.getStrandedInterval("i1")
            checkNotNull(parked) { "an open interval at start-up has nothing timing it" }
            assertThat(parked.isSubTaskInterval).isFalse()
            assertThat(parked.detectedAtEpochMs).isEqualTo(threeDaysLater.toEpochMilliseconds())

            // The whole point: three days passed, and not one millisecond of it was banked.
            assertThat(db.projectDao.getTaskById("t1")!!.durationMillis).isEqualTo(0L)
            assertThat(db.projectDao.getIntervalById("i1")!!.durationMillis).isEqualTo(0L)
            assertThat(db.projectDao.getIntervalById("i1")!!.endDateTimeEpochMs).isNull()
        }

    @Test
    fun parkingClearsTheTimerFlagSoTheCardStopsShowingPause() =
        runBlocking {
            seed()
            openTaskInterval()

            reconciler.reconcile()

            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isFalse()
        }

    @Test
    fun aParkedIntervalIsInvisibleToTheTimer() =
        runBlocking {
            seed()
            openTaskInterval()

            reconciler.reconcile()

            // Nothing may adopt it: not a task start, and not a subtask looking for a parent to nest in.
            assertThat(db.projectDao.getOpenIntervalBySessionId("t1")).isNull()
        }

    @Test
    fun runningTwiceDoesNotMoveTheProposedEnd() =
        runBlocking {
            seed()
            openTaskInterval()
            timeProvider.now = threeDaysLater
            reconciler.reconcile()

            // A second pass a day later - the gate is re-entrant, and so is a relaunch.
            timeProvider.now = Instant.fromEpochMilliseconds(threeDaysLater.toEpochMilliseconds() + 86_400_000)
            reconciler.reconcile()

            // Still the first detection: otherwise the duration the dialog offers grows every launch.
            assertThat(
                db.projectDao.getStrandedInterval("i1")!!.detectedAtEpochMs,
            ).isEqualTo(threeDaysLater.toEpochMilliseconds())
            assertThat(
                db.projectDao
                    .observeStrandedIntervals()
                    .first()
                    .size,
            ).isEqualTo(1)
        }

    @Test
    fun aClosedIntervalIsLeftAlone() =
        runBlocking {
            seed()
            db.projectDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i-closed",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = 60_000,
                    durationMillis = 60_000,
                ),
            )
            timeProvider.now = threeDaysLater

            reconciler.reconcile()

            assertThat(db.projectDao.getStrandedInterval("i-closed")).isNull()
            assertThat(db.projectDao.getIntervalById("i-closed")!!.durationMillis).isEqualTo(60_000L)
        }

    @Test
    fun aStrandedSubTaskIntervalIsParkedAlongsideTheTaskIntervalItSitsIn() =
        runBlocking {
            seed(withSubTask = true)
            openTaskInterval()
            db.projectDao.upsertSubTaskInterval(
                SubTaskIntervalEntity(
                    subTaskIntervalId = "si1",
                    parentSubTaskId = "s1",
                    parentTaskIntervalId = "i1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                    startedParentTimer = true,
                ),
            )
            timeProvider.now = threeDaysLater

            reconciler.reconcile()

            val parkedChild = db.projectDao.getStrandedInterval("si1")
            val parkedParent = db.projectDao.getStrandedInterval("i1")
            checkNotNull(parkedChild)
            checkNotNull(parkedParent)
            assertThat(parkedChild.isSubTaskInterval).isTrue()
            assertThat(parkedParent.isSubTaskInterval).isFalse()
            // One clock read for the whole pass, so the child's proposed span can never outrun its
            // parent's.
            assertThat(parkedChild.detectedAtEpochMs).isEqualTo(parkedParent.detectedAtEpochMs)
            assertThat(db.projectDao.getSubTaskById("s1")!!.isTimerRunning).isFalse()
            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isFalse()
        }

    // ---- device scoping ------------------------------------------------------------------------

    @Test
    fun anIntervalAnotherDeviceStartedIsLeftRunning() =
        runBlocking {
            seed()
            openTaskInterval(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)
            timeProvider.now = threeDaysLater

            reconciler.reconcile()

            // The user's other phone is tracking right now. Parking it would hide a live timer and ask
            // them to adjudicate a session that has not ended.
            assertThat(db.projectDao.observeOpenTaskInterval().first()).isNotNull()
            assertThat(db.projectDao.getStrandedInterval("i1")).isNull()
        }

    @Test
    fun aForeignTimerDoesNotHaveItsTaskFlagCleared() =
        runBlocking {
            seed()
            openTaskInterval(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)

            reconciler.reconcile()

            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isTrue()
        }

    @Test
    fun anIntervalWithNoDeviceIdIsTreatedAsThisDevices() =
        runBlocking {
            seed()
            openTaskInterval(startedByDeviceId = null)

            reconciler.reconcile()

            // Every row written before the column existed. Reading null as foreign would strand this
            // device's own crashed timers with nothing ever offering to recover them.
            assertThat(db.projectDao.getStrandedInterval("i1")).isNotNull()
            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isFalse()
        }

    @Test
    fun ownAndForeignOpenIntervalsAreSeparatedInOnePass() =
        runBlocking {
            seed()
            openTaskInterval(id = "mine", startedByDeviceId = FakeDeviceIdProvider.THIS_DEVICE)
            openTaskInterval(id = "theirs", startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)

            reconciler.reconcile()

            assertThat(db.projectDao.getStrandedInterval("mine")).isNotNull()
            assertThat(db.projectDao.getStrandedInterval("theirs")).isNull()
        }

    @Test
    fun aForeignSubTaskIntervalIsLeftRunningToo() =
        runBlocking {
            seed(withSubTask = true)
            openTaskInterval(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)
            openSubTaskInterval(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)

            reconciler.reconcile()

            assertThat(db.projectDao.getStrandedInterval("si1")).isNull()
            assertThat(db.projectDao.getStrandedInterval("i1")).isNull()
            assertThat(db.projectDao.getSubTaskById("s1")!!.isTimerRunning).isTrue()
        }

    @Test
    fun thisDevicesSubTaskIntervalIsStillParkedEvenUnderAForeignParent() =
        runBlocking {
            seed(withSubTask = true)
            openTaskInterval(startedByDeviceId = FakeDeviceIdProvider.OTHER_DEVICE)
            openSubTaskInterval(startedByDeviceId = FakeDeviceIdProvider.THIS_DEVICE)

            reconciler.reconcile()

            // Children are walked first and judged on their own provenance, so a crash here is still
            // recovered even though the enclosing interval belongs to another device.
            assertThat(db.projectDao.getStrandedInterval("si1")).isNotNull()
            assertThat(db.projectDao.getStrandedInterval("i1")).isNull()
        }
}
