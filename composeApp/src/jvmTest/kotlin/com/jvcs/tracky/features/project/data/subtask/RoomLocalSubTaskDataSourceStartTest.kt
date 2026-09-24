package com.jvcs.tracky.features.project.data.subtask

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
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.testServerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dataSource =
            RoomLocalSubTaskDataSource(
                db.projectDao,
                db.subTaskDao,
                db.taskDao,
                db.subTaskIntervalDao,
                db.taskIntervalDao,
                db.strandedIntervalDao,
                FakeDeviceIdProvider(),
                testServerClock(timeProvider),
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(vararg subTaskIds: String) {
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
        db.taskDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "task",
                description = null,
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = null,
            ),
        )
        subTaskIds.forEach { id ->
            db.subTaskDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    projectSubTaskId = id,
                    parentProjectTaskId = "t1",
                    parentProjectId = "p1",
                    title = "sub-$id",
                    description = null,
                    durationMillis = null,
                    isTimerRunning = false,
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    isFinished = false,
                    updatedAtEpochMs = null,
                ),
            )
        }
    }

    private suspend fun taskIsRunning(): Boolean = db.taskDao.getTaskById("t1")!!.isTimerRunning

    private suspend fun subTaskIsRunning(id: String): Boolean = db.subTaskDao.getSubTaskById(id)!!.isTimerRunning

    @Test
    fun startingASubTaskWhoseTaskIsIdleOpensTheTaskTimerToo() =
        runBlocking {
            seed("s1")

            val result = dataSource.startSubTask("s1")

            check(result is Result.Success)
            val openTaskInterval = db.taskIntervalDao.getOpenIntervalBySessionId("t1")
            checkNotNull(openTaskInterval) { "the subtask interval needs a task interval to sit in" }
            assertThat(result.data.subTaskInterval.parentTaskIntervalId).isEqualTo(openTaskInterval.intervalId)
            assertThat(taskIsRunning()).isTrue()
            assertThat(subTaskIsRunning("s1")).isTrue()
            // It opened the parent, so stopping it later has to close the parent again.
            assertThat(result.data.subTaskInterval.startedParentTimer).isTrue()
            // And it is handed back, because that row syncs and only the caller can push it.
            assertThat(result.data.taskInterval?.intervalId).isEqualTo(openTaskInterval.intervalId)
        }

    @Test
    fun startingASubTaskWhoseTaskIsAlreadyRunningNestsInTheOpenInterval() =
        runBlocking {
            seed("s1")
            db.taskIntervalDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i-manual",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                ),
            )
            db.taskDao.updateSessionTimerStatus("t1", true)
            timeProvider.now = Instant.fromEpochMilliseconds(5_000)

            val result = dataSource.startSubTask("s1")

            check(result is Result.Success)
            assertThat(result.data.subTaskInterval.parentTaskIntervalId).isEqualTo("i-manual")
            // Nothing new to push: that interval is already on its way to the server.
            assertThat(result.data.taskInterval).isNull()
            // The task timer was the user's doing, so this subtask must not claim it.
            assertThat(result.data.subTaskInterval.startedParentTimer).isFalse()
            assertThat(
                db.taskDao
                    .getTaskWithSubTasksById("t1")
                    .first()!!
                    .intervals.size,
            ).isEqualTo(1)
        }

    @Test
    fun startingASecondSubTaskClosesTheFirstOne() =
        runBlocking {
            seed("s1", "s2")
            dataSource.startSubTask("s1")
            timeProvider.now = Instant.fromEpochMilliseconds(30_000)

            dataSource.startSubTask("s2")

            // s1 is closed at exactly the instant s2 starts, so the two never overlap.
            assertThat(db.subTaskIntervalDao.getOpenSubTaskInterval("s1")).isNull()
            assertThat(db.subTaskIntervalDao.getOpenSubTaskInterval("s2")).isNotNull()
            assertThat(subTaskIsRunning("s1")).isFalse()
            assertThat(subTaskIsRunning("s2")).isTrue()
            assertThat(db.subTaskDao.getSubTaskById("s1")!!.durationMillis).isEqualTo(30_000L)
        }

    @Test
    fun theSecondSubTaskReusesTheTaskIntervalTheFirstOneOpened() =
        runBlocking {
            seed("s1", "s2")
            val first = dataSource.startSubTask("s1")
            timeProvider.now = Instant.fromEpochMilliseconds(30_000)

            val second = dataSource.startSubTask("s2")

            check(first is Result.Success && second is Result.Success)
            assertThat(
                second.data.subTaskInterval.parentTaskIntervalId,
            ).isEqualTo(first.data.subTaskInterval.parentTaskIntervalId)
            // Only s1 may claim the parent: if s2 claimed it too, stopping either would stop the task.
            assertThat(first.data.subTaskInterval.startedParentTimer).isTrue()
            assertThat(second.data.subTaskInterval.startedParentTimer).isFalse()
        }

    @Test
    fun startingAnUnknownSubTaskFailsWithoutTouchingTheTask() =
        runBlocking {
            seed("s1")

            val result = dataSource.startSubTask("nope")

            assertThat(result is Result.Error).isTrue()
            assertThat(db.taskIntervalDao.getOpenIntervalBySessionId("t1")).isNull()
            assertThat(taskIsRunning()).isFalse()
        }
}
