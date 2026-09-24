package com.jvcs.tracky.features.project.data.interval

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.inMemoryTrackyDatabase
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

internal class RoomLocalIntervalDataSourceTest {

    private val db = inMemoryTrackyDatabase()
    private val intervals = RoomLocalIntervalDataSource(db.taskIntervalDao)

    @BeforeTest
    fun seed() =
        runBlocking {
            db.projectDao.upsertProject(
                ProjectEntity("p1", "Project", null, null, null, 0L, isFinished = false, endDateTimeEpochMs = null),
            )
            db.taskDao.upsertProjectTask(
                ProjectTaskEntity("t1", "p1", "Task", null, 0L, 0L, null, isFinished = false, isTimerRunning = false),
            )
        }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun anOpenIntervalIsFoundByItsTaskUntilItIsDeleted() =
        runBlocking {
            intervals.upsertTaskInterval(interval("closed", end = 60_000L))
            intervals.upsertTaskInterval(interval("open", end = null))

            assertThat(
                intervals.getIntervalById("closed").map { it?.durationMillis },
            ).isEqualTo(Result.Success(60_000L))
            assertThat(intervals.getOpenIntervalByTaskId("t1").map { it?.intervalId }).isEqualTo(Result.Success("open"))

            intervals.deleteTaskInterval("open")

            assertThat(intervals.getOpenIntervalByTaskId("t1").map { it?.intervalId }).isEqualTo(Result.Success(null))
        }

    private fun interval(id: String, end: Long?) =
        TaskInterval(
            intervalId = id,
            parentTaskId = "t1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = end?.let(Instant::fromEpochMilliseconds),
            durationMillis = end ?: 0L,
        )
}
