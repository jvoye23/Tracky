package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        reconciler = StrandedTimerReconciler(db.projectDao, timeProvider, TestScope())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(withSubTask: Boolean = false) {
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
                durationMillis = 0, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                isFinished = false, isTimerRunning = true, updatedAtEpochMs = null
            )
        )
        if (withSubTask) {
            db.projectDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = "s1", parentProjectTaskId = "t1", parentProjectId = "p1",
                    title = "sub", description = null, durationMillis = null,
                    isTimerRunning = true, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                    isFinished = false, updatedAtEpochMs = null
                )
            )
        }
    }

    private suspend fun openTaskInterval(id: String = "i1", startedAt: Long = 0) {
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = id, parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = startedAt, endDateTimeEpochMs = null, durationMillis = 0
            )
        )
    }

    @Test
    fun anIntervalLeftOpenByAPreviousProcessIsParkedWithoutBankingAnything() = runBlocking {
        seed()
        openTaskInterval()
        timeProvider.now = threeDaysLater

        reconciler.reconcile()

        val parked = db.projectDao.getStrandedInterval("i1")
        assertNotNull(parked, "an open interval at start-up has nothing timing it")
        assertFalse(parked.isSubTaskInterval)
        assertEquals(threeDaysLater.toEpochMilliseconds(), parked.detectedAtEpochMs)

        // The whole point: three days passed, and not one millisecond of it was banked.
        assertEquals(0L, db.projectDao.getTaskById("t1")!!.durationMillis)
        assertEquals(0L, db.projectDao.getIntervalById("i1")!!.durationMillis)
        assertNull(db.projectDao.getIntervalById("i1")!!.endDateTimeEpochMs)
    }

    @Test
    fun parkingClearsTheTimerFlagSoTheCardStopsShowingPause() = runBlocking {
        seed()
        openTaskInterval()

        reconciler.reconcile()

        assertFalse(db.projectDao.getTaskById("t1")!!.isTimerRunning)
    }

    @Test
    fun aParkedIntervalIsInvisibleToTheTimer() = runBlocking {
        seed()
        openTaskInterval()

        reconciler.reconcile()

        // Nothing may adopt it: not a task start, and not a subtask looking for a parent to nest in.
        assertNull(db.projectDao.getOpenIntervalBySessionId("t1"))
    }

    @Test
    fun runningTwiceDoesNotMoveTheProposedEnd() = runBlocking {
        seed()
        openTaskInterval()
        timeProvider.now = threeDaysLater
        reconciler.reconcile()

        // A second pass a day later - the gate is re-entrant, and so is a relaunch.
        timeProvider.now = Instant.fromEpochMilliseconds(threeDaysLater.toEpochMilliseconds() + 86_400_000)
        reconciler.reconcile()

        // Still the first detection: otherwise the duration the dialog offers grows every launch.
        assertEquals(
            threeDaysLater.toEpochMilliseconds(),
            db.projectDao.getStrandedInterval("i1")!!.detectedAtEpochMs
        )
        assertEquals(1, db.projectDao.observeStrandedIntervals().first().size)
    }

    @Test
    fun aClosedIntervalIsLeftAlone() = runBlocking {
        seed()
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i-closed", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = 0, endDateTimeEpochMs = 60_000, durationMillis = 60_000
            )
        )
        timeProvider.now = threeDaysLater

        reconciler.reconcile()

        assertNull(db.projectDao.getStrandedInterval("i-closed"))
        assertEquals(60_000L, db.projectDao.getIntervalById("i-closed")!!.durationMillis)
    }

    @Test
    fun aStrandedSubTaskIntervalIsParkedAlongsideTheTaskIntervalItSitsIn() = runBlocking {
        seed(withSubTask = true)
        openTaskInterval()
        db.projectDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = "si1", parentSubTaskId = "s1", parentTaskIntervalId = "i1",
                parentProjectId = "p1", startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                durationMillis = 0, startedParentTimer = true
            )
        )
        timeProvider.now = threeDaysLater

        reconciler.reconcile()

        val parkedChild = db.projectDao.getStrandedInterval("si1")
        val parkedParent = db.projectDao.getStrandedInterval("i1")
        assertNotNull(parkedChild)
        assertNotNull(parkedParent)
        assertTrue(parkedChild.isSubTaskInterval)
        assertFalse(parkedParent.isSubTaskInterval)
        // One clock read for the whole pass, so the child's proposed span can never outrun its
        // parent's.
        assertEquals(parkedParent.detectedAtEpochMs, parkedChild.detectedAtEpochMs)
        assertFalse(db.projectDao.getSubTaskById("s1")!!.isTimerRunning)
        assertFalse(db.projectDao.getTaskById("t1")!!.isTimerRunning)
    }
}
