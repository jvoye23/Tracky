package com.jvcs.tracky.features.project.data.timer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
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
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testServerClock
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        repository =
            OfflineFirstStrandedTimerRepository(
                projectDao = db.projectDao,
                subTaskDao = db.subTaskDao,
                taskDao = db.taskDao,
                subTaskIntervalDao = db.subTaskIntervalDao,
                taskIntervalDao = db.taskIntervalDao,
                strandedIntervalDao = db.strandedIntervalDao,
                intervalRepository = FakeIntervalRepository(),
                subTaskIntervalRepository = FakeSubTaskIntervalRepository(),
                projectTaskRepository = FakeProjectTaskRepository(),
                subTaskRepository = FakeSubTaskRepository(),
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seedParkedTaskInterval(withSubTask: Boolean = false) {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1",
                title = "Tracky App",
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
        db.taskDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "Project Detail Screen",
                description = null,
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = null,
            ),
        )
        db.taskIntervalDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1",
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = startedAt,
                endDateTimeEpochMs = null,
                durationMillis = 0,
            ),
        )
        if (withSubTask) {
            db.subTaskDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = "s1",
                    parentProjectTaskId = "t1",
                    parentProjectId = "p1",
                    title = "Per-day strip",
                    description = null,
                    durationMillis = 0,
                    isTimerRunning = false,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    updatedAtEpochMs = null,
                ),
            )
            db.subTaskIntervalDao.upsertSubTaskInterval(
                SubTaskIntervalEntity(
                    subTaskIntervalId = "si1",
                    parentSubTaskId = "s1",
                    parentTaskIntervalId = "i1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = startedAt,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                    startedParentTimer = true,
                ),
            )
        }
        timeProvider.now = Instant.fromEpochMilliseconds(detectedAt)
        StrandedTimerReconciler(
            db.subTaskDao,
            db.taskDao,
            db.subTaskIntervalDao,
            db.taskIntervalDao,
            db.strandedIntervalDao,
            testServerClock(timeProvider),
            FakeDeviceIdProvider(),
            kotlinx.coroutines.test.TestScope(),
        ).reconcile()
    }

    @Test
    fun aParkedTaskIntervalSurfacesAsOneReviewItemNamingItsProjectAndTask() =
        runBlocking {
            seedParkedTaskInterval()

            val timers = repository.observeStrandedTimers().first()

            assertThat(timers.size).isEqualTo(1)
            val timer = timers.single()
            assertThat(timer.taskTitle).isEqualTo("Project Detail Screen")
            assertThat(timer.projectTitle).isEqualTo("Tracky App")
            assertThat(timer.proposedEndAt.toEpochMilliseconds()).isEqualTo(detectedAt)
            // The reported number: 75h21m, offered but not banked.
            assertThat(timer.proposedDuration).isEqualTo(75.hours + 21.minutes)
            assertThat(db.strandedIntervalDao.getStrandedInterval("i1")).isNotNull()
            assertThat(db.taskDao.getTaskById("t1")!!.durationMillis).isEqualTo(0L)
        }

    @Test
    fun keepingBanksTheOfferedSpanAndClosesTheInterval() =
        runBlocking {
            seedParkedTaskInterval()
            val timer = repository.observeStrandedTimers().first().single()

            val result = repository.keep(timer)

            assertThat(result is Result.Success).isTrue()
            val closed = db.taskIntervalDao.getIntervalById("i1")!!
            assertThat(closed.endDateTimeEpochMs).isEqualTo(detectedAt)
            assertThat(closed.durationMillis).isEqualTo(detectedAt)
            assertThat(db.taskDao.getTaskById("t1")!!.durationMillis).isEqualTo(detectedAt)
            // Resolved once, gone for good.
            assertThat(db.strandedIntervalDao.getStrandedInterval("i1")).isNull()
            assertThat(repository.observeStrandedTimers().first().isEmpty()).isTrue()
            // And the closed row reaches the server.
            assertThat(pushedIntervalUpdates.map { it.intervalId }).isEqualTo(listOf("i1"))
            assertThat(pushedTasks.map { it.projectTaskId }).isEqualTo(listOf("t1"))
        }

    @Test
    fun editingBanksTheTypedDurationInsteadOfTheOfferedOne() =
        runBlocking {
            seedParkedTaskInterval()
            val timer = repository.observeStrandedTimers().first().single()

            repository.keepWithDuration(timer, 2.hours)

            val closed = db.taskIntervalDao.getIntervalById("i1")!!
            assertThat(closed.durationMillis).isEqualTo(2 * 60 * 60 * 1000L)
            assertThat(closed.endDateTimeEpochMs).isEqualTo(2 * 60 * 60 * 1000L)
            assertThat(db.taskDao.getTaskById("t1")!!.durationMillis).isEqualTo(2 * 60 * 60 * 1000L)
        }

    @Test
    fun discardingRemovesTheRowLocallyAndOnTheServer() =
        runBlocking {
            seedParkedTaskInterval()
            val timer = repository.observeStrandedTimers().first().single()

            repository.discard(timer)

            assertThat(db.strandedIntervalDao.getStrandedInterval("i1")).isNull()
            // Remote too: upsertServerTree re-inserts an interval the local side no longer has, so a
            // local-only delete would come back on the next pull.
            assertThat(deletedIntervalIds).isEqualTo(listOf("i1"))
            assertThat(db.taskDao.getTaskById("t1")!!.durationMillis).isEqualTo(0L)
        }

    @Test
    fun aParkedSubTaskIntervalAndItsParentAreOneItemResolvedTogether() =
        runBlocking {
            seedParkedTaskInterval(withSubTask = true)

            val timers = repository.observeStrandedTimers().first()

            assertThat(timers.size, name = "the nested pair is one stretch of wall clock, not two").isEqualTo(1)
            val timer = timers.single()
            assertThat(timer.subTaskIntervalId).isEqualTo("si1")
            assertThat(timer.taskIntervalId).isEqualTo("i1")
            assertThat(timer.subTaskTitle).isEqualTo("Per-day strip")

            repository.keep(timer)

            assertThat(db.subTaskIntervalDao.getSubTaskIntervalById("si1")!!.endDateTimeEpochMs).isEqualTo(detectedAt)
            assertThat(db.taskIntervalDao.getIntervalById("i1")!!.endDateTimeEpochMs).isEqualTo(detectedAt)
            assertThat(db.strandedIntervalDao.getStrandedInterval("si1")).isNull()
            assertThat(db.strandedIntervalDao.getStrandedInterval("i1")).isNull()
            assertThat(repository.observeStrandedTimers().first().isEmpty()).isTrue()
        }

    @Test
    fun aTaskLevelIntervalOnATaskWithSubTasksIsFlaggedAsNotWorthKeeping() =
        runBlocking {
            seedParkedTaskInterval()
            // The subtask arrives after the parking, so the pair is not formed but the warning applies.
            db.subTaskDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = "s1",
                    parentProjectTaskId = "t1",
                    parentProjectId = "p1",
                    title = "Per-day strip",
                    description = null,
                    durationMillis = 0,
                    isTimerRunning = false,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    updatedAtEpochMs = null,
                ),
            )

            val timer = repository.observeStrandedTimers().first().single()

            assertThat(
                timer.keepingWouldNotBeCounted,
                name = "rule 1 counts subtasks only, so this time would inflate totals and render nowhere",
            ).isTrue()
        }

    @Test
    fun anEditShorterThanTheSubTaskStartDiscardsItRatherThanWritingANegativeSpan() =
        runBlocking {
            seedParkedTaskInterval(withSubTask = true)
            // Push the subtask's start past where a one-hour edit would place the end.
            db.subTaskIntervalDao.upsertSubTaskInterval(
                db.subTaskIntervalDao.getSubTaskIntervalById("si1")!!.copy(startDateTimeEpochMs = 5 * 60 * 60 * 1000L),
            )
            val timer = repository.observeStrandedTimers().first().single()

            repository.keepWithDuration(timer, 1.hours)

            assertThat(deletedSubTaskIntervalIds).isEqualTo(listOf("si1"))
            assertThat(db.strandedIntervalDao.getStrandedInterval("si1")).isNull()
            // The task interval still closes at the edited end.
            assertThat(db.taskIntervalDao.getIntervalById("i1")!!.durationMillis).isEqualTo(60 * 60 * 1000L)
        }

    // --- fakes: the push side, recorded rather than performed ------------------------------------

    private inner class FakeIntervalRepository : IntervalRepository {
        override suspend fun createTaskInterval(interval: TaskInterval): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun updateTaskInterval(interval: TaskInterval): EmptyResult<DataError> {
            pushedIntervalUpdates += interval
            return Result.Success(Unit)
        }

        override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError> {
            deletedIntervalIds += intervalId
            return Result.Success(Unit)
        }

        override suspend fun getOpenIntervalByTaskId(taskId: String) = Result.Success(null)

        override suspend fun syncPendingIntervals(): EmptyResult<DataError> = Result.Success(Unit)
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

        override suspend fun syncPendingSubTaskIntervals(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private inner class FakeProjectTaskRepository : ProjectTaskRepository {
        override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> {
            pushedTasks += projectTask
            return Result.Success(Unit)
        }

        override suspend fun deleteProjectTask(projectId: String, taskId: String): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun updateProjectTaskDuration(
            taskId: String,
            newDurationMillis: Long,
        ): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun updateProjectTaskTitle(taskId: String, title: String): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun updateProjectTaskText(
            taskId: String,
            title: String,
            description: String?,
        ) = Result.Success(Unit)

        override fun getProjectTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)

        override suspend fun startProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun stopProjectTask(taskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun reorderTasks(projectId: String, orderedTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun syncPendingTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private inner class FakeSubTaskRepository : SubTaskRepository {
        override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = flowOf(emptyList())

        override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> = Result.Success(Unit)

        override suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError> = Result.Success(null)

        override suspend fun reorderSubTasks(taskId: String, orderedSubTaskIds: List<String>): EmptyResult<DataError> =
            Result.Success(Unit)

        override suspend fun syncPendingSubTasks(): EmptyResult<DataError> = Result.Success(Unit)
    }
}
