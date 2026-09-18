package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Resolving a parked timer: what gets banked, what gets pushed, and what is left behind.
 *
 * Every test starts from the reported bug's shape — a timer started on day one, noticed three days
 * later — because that is the case where keeping and discarding differ by 75 hours.
 */
internal class OfflineFirstStrandedTimerRepositoryTest {

    private lateinit var db: TrackyDatabase
    private lateinit var repository: OfflineFirstStrandedTimerRepository
    private val timeProvider = FakeTimeProvider()

    private val startedAt = 0L
    private val detectedAt = 3 * 24 * 60 * 60 * 1_000L + (3 * 60 + 21) * 60 * 1_000L // 75h21m

    private val pushedIntervalUpdates = mutableListOf<TaskInterval>()
    private val deletedIntervalIds = mutableListOf<String>()
    private val pushedSubTaskIntervalUpdates = mutableListOf<SubTaskInterval>()
    private val deletedSubTaskIntervalIds = mutableListOf<String>()
    private val pushedTasks = mutableListOf<ProjectTask>()

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        repository = OfflineFirstStrandedTimerRepository(
            projectDao = db.projectDao,
            intervalRepository = FakeIntervalRepository(),
            subTaskIntervalRepository = FakeSubTaskIntervalRepository(),
            projectTaskRepository = FakeProjectTaskRepository(),
            subTaskRepository = FakeSubTaskRepository()
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seedParkedTaskInterval(withSubTask: Boolean = false) {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1", title = "Tracky App", description = null, color = null,
                totalDuration = null, startDateTimeEpochMs = 0, isFinished = false,
                useLightTextColor = false, endDateTimeEpochMs = null, isArchived = false,
                trashedAtEpochMs = null, isPinned = false, updatedAtEpochMs = null
            )
        )
        db.projectDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1", parentProjectId = "p1", title = "Project Detail Screen",
                description = null, durationMillis = 0, startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null, isFinished = false, isTimerRunning = false,
                updatedAtEpochMs = null
            )
        )
        db.projectDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1", parentTaskId = "t1", parentProjectId = "p1",
                startDateTimeEpochMs = startedAt, endDateTimeEpochMs = null, durationMillis = 0
            )
        )
        if (withSubTask) {
            db.projectDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = "s1", parentProjectTaskId = "t1", parentProjectId = "p1",
                    title = "Per-day strip", description = null, durationMillis = 0,
                    isTimerRunning = false, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                    isFinished = false, updatedAtEpochMs = null
                )
            )
            db.projectDao.upsertSubTaskInterval(
                SubTaskIntervalEntity(
                    subTaskIntervalId = "si1", parentSubTaskId = "s1", parentTaskIntervalId = "i1",
                    parentProjectId = "p1", startDateTimeEpochMs = startedAt,
                    endDateTimeEpochMs = null, durationMillis = 0, startedParentTimer = true
                )
            )
        }
        timeProvider.now = Instant.fromEpochMilliseconds(detectedAt)
        StrandedTimerReconciler(db.projectDao, timeProvider, kotlinx.coroutines.test.TestScope())
            .reconcile()
    }

    @Test
    fun aParkedTaskIntervalSurfacesAsOneReviewItemNamingItsProjectAndTask() = runBlocking {
        seedParkedTaskInterval()

        val timers = repository.observeStrandedTimers().first()

        assertEquals(1, timers.size)
        val timer = timers.single()
        assertEquals("Project Detail Screen", timer.taskTitle)
        assertEquals("Tracky App", timer.projectTitle)
        assertEquals(detectedAt, timer.proposedEndAt.toEpochMilliseconds())
        // The reported number: 75h21m, offered but not banked.
        assertEquals(75.hours + 21.minutes, timer.proposedDuration)
        assertNotNull(db.projectDao.getStrandedInterval("i1"))
        assertEquals(0L, db.projectDao.getTaskById("t1")!!.durationMillis)
    }

    @Test
    fun keepingBanksTheOfferedSpanAndClosesTheInterval() = runBlocking {
        seedParkedTaskInterval()
        val timer = repository.observeStrandedTimers().first().single()

        val result = repository.keep(timer)

        assertTrue(result is Result.Success)
        val closed = db.projectDao.getIntervalById("i1")!!
        assertEquals(detectedAt, closed.endDateTimeEpochMs)
        assertEquals(detectedAt, closed.durationMillis)
        assertEquals(detectedAt, db.projectDao.getTaskById("t1")!!.durationMillis)
        // Resolved once, gone for good.
        assertNull(db.projectDao.getStrandedInterval("i1"))
        assertTrue(repository.observeStrandedTimers().first().isEmpty())
        // And the closed row reaches the server.
        assertEquals(listOf("i1"), pushedIntervalUpdates.map { it.intervalId })
        assertEquals(listOf("t1"), pushedTasks.map { it.projectTaskId })
    }

    @Test
    fun editingBanksTheTypedDurationInsteadOfTheOfferedOne() = runBlocking {
        seedParkedTaskInterval()
        val timer = repository.observeStrandedTimers().first().single()

        repository.keepWithDuration(timer, 2.hours)

        val closed = db.projectDao.getIntervalById("i1")!!
        assertEquals(2 * 60 * 60 * 1000L, closed.durationMillis)
        assertEquals(2 * 60 * 60 * 1000L, closed.endDateTimeEpochMs)
        assertEquals(2 * 60 * 60 * 1000L, db.projectDao.getTaskById("t1")!!.durationMillis)
    }

    @Test
    fun discardingRemovesTheRowLocallyAndOnTheServer() = runBlocking {
        seedParkedTaskInterval()
        val timer = repository.observeStrandedTimers().first().single()

        repository.discard(timer)

        assertNull(db.projectDao.getStrandedInterval("i1"))
        // Remote too: upsertServerTree re-inserts an interval the local side no longer has, so a
        // local-only delete would come back on the next pull.
        assertEquals(listOf("i1"), deletedIntervalIds)
        assertEquals(0L, db.projectDao.getTaskById("t1")!!.durationMillis)
    }

    @Test
    fun aParkedSubTaskIntervalAndItsParentAreOneItemResolvedTogether() = runBlocking {
        seedParkedTaskInterval(withSubTask = true)

        val timers = repository.observeStrandedTimers().first()

        assertEquals(1, timers.size, "the nested pair is one stretch of wall clock, not two")
        val timer = timers.single()
        assertEquals("si1", timer.subTaskIntervalId)
        assertEquals("i1", timer.taskIntervalId)
        assertEquals("Per-day strip", timer.subTaskTitle)

        repository.keep(timer)

        assertEquals(detectedAt, db.projectDao.getSubTaskIntervalById("si1")!!.endDateTimeEpochMs)
        assertEquals(detectedAt, db.projectDao.getIntervalById("i1")!!.endDateTimeEpochMs)
        assertNull(db.projectDao.getStrandedInterval("si1"))
        assertNull(db.projectDao.getStrandedInterval("i1"))
        assertTrue(repository.observeStrandedTimers().first().isEmpty())
    }

    @Test
    fun aTaskLevelIntervalOnATaskWithSubTasksIsFlaggedAsNotWorthKeeping() = runBlocking {
        seedParkedTaskInterval()
        // The subtask arrives after the parking, so the pair is not formed but the warning applies.
        db.projectDao.upsertProjectSubTask(
            ProjectSubTaskEntity(
                projectSubTaskId = "s1", parentProjectTaskId = "t1", parentProjectId = "p1",
                title = "Per-day strip", description = null, durationMillis = 0,
                isTimerRunning = false, startDateTimeEpochMs = 0, endDateTimeEpochMs = null,
                isFinished = false, updatedAtEpochMs = null
            )
        )

        val timer = repository.observeStrandedTimers().first().single()

        assertTrue(
            timer.keepingWouldNotBeCounted,
            "rule 1 counts subtasks only, so this time would inflate totals and render nowhere"
        )
    }

    @Test
    fun anEditShorterThanTheSubTaskStartDiscardsItRatherThanWritingANegativeSpan() = runBlocking {
        seedParkedTaskInterval(withSubTask = true)
        // Push the subtask's start past where a one-hour edit would place the end.
        db.projectDao.upsertSubTaskInterval(
            db.projectDao.getSubTaskIntervalById("si1")!!.copy(startDateTimeEpochMs = 5 * 60 * 60 * 1000L)
        )
        val timer = repository.observeStrandedTimers().first().single()

        repository.keepWithDuration(timer, 1.hours)

        assertEquals(listOf("si1"), deletedSubTaskIntervalIds)
        assertNull(db.projectDao.getStrandedInterval("si1"))
        // The task interval still closes at the edited end.
        assertEquals(60 * 60 * 1000L, db.projectDao.getIntervalById("i1")!!.durationMillis)
    }

    // --- fakes: the push side, recorded rather than performed ------------------------------------

    private inner class FakeIntervalRepository : IntervalRepository {
        override suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
            pushedIntervalUpdates += interval
            return Result.Success(Unit)
        }
        override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError> {
            deletedIntervalIds += intervalId
            return Result.Success(Unit)
        }
        override suspend fun getOpenIntervalByTaskId(taskId: String) = Result.Success(null)
        override suspend fun syncPendingIntervals() = Unit
    }

    private inner class FakeSubTaskIntervalRepository : SubTaskIntervalRepository {
        override suspend fun createSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError> {
            pushedSubTaskIntervalUpdates += interval
            return Result.Success(Unit)
        }
        override suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError> {
            deletedSubTaskIntervalIds += intervalId
            return Result.Success(Unit)
        }
        override suspend fun getOpenIntervalBySubTaskId(subTaskId: String) = Result.Success(null)
        override suspend fun syncPendingSubTaskIntervals() = Unit
    }

    private inner class FakeProjectTaskRepository : ProjectTaskRepository {
        override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
            pushedTasks += projectTask
            return Result.Success(Unit)
        }
        override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateProjectTaskDuration(taskId: String, newDurationMillis: Long): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
            Result.Success(Unit)
        override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)
        override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)
        override suspend fun syncPendingTasks() = Unit
    }

    private inner class FakeSubTaskRepository : SubTaskRepository {
        override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = flowOf(emptyList())
        override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)
        override suspend fun lastStartedSubTaskId(taskId: String): String? = null
        override suspend fun syncPendingSubTasks() = Unit
    }
}
