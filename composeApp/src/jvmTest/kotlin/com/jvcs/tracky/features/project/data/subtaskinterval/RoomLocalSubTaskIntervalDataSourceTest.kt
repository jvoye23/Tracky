package com.jvcs.tracky.features.project.data.subtaskinterval

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.database.inMemoryTrackyDatabase
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

internal class RoomLocalSubTaskIntervalDataSourceTest {

    private val db = inMemoryTrackyDatabase()
    private val intervals = RoomLocalSubTaskIntervalDataSource(db.subTaskIntervalDao)

    /** A subtask interval needs both parents: its subtask and the task interval it sits inside. */
    @BeforeTest
    fun seed() =
        runBlocking {
            db.projectDao.upsertProject(
                ProjectEntity("p1", "Project", null, null, null, 0L, isFinished = false, endDateTimeEpochMs = null),
            )
            db.taskDao.upsertProjectTask(
                ProjectTaskEntity("t1", "p1", "Task", null, 0L, 0L, null, isFinished = false, isTimerRunning = false),
            )
            db.taskIntervalDao.upsertTaskInterval(TaskIntervalEntity("i1", "t1", "p1", 0L, null, 0L))
            db.subTaskDao.upsertProjectSubTask(
                ProjectSubTaskEntity(
                    "s1",
                    "t1",
                    "p1",
                    "Subtask",
                    null,
                    0L,
                    isTimerRunning = true,
                    0L,
                    null,
                    isFinished = false,
                ),
            )
        }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun anOpenSubTaskIntervalIsFoundUntilItIsDeleted() =
        runBlocking {
            intervals.upsertSubTaskInterval(interval("closed", end = 30_000L))
            intervals.upsertSubTaskInterval(interval("open", end = null))

            assertThat(
                intervals.getSubTaskIntervalById("closed").map { it?.durationMillis },
            ).isEqualTo(Result.Success(30_000L))
            assertThat(
                intervals.getOpenIntervalBySubTaskId("s1").map { it?.subTaskIntervalId },
            ).isEqualTo(Result.Success("open"))

            intervals.deleteSubTaskInterval("open")

            assertThat(
                intervals.getOpenIntervalBySubTaskId("s1").map { it?.subTaskIntervalId },
            ).isEqualTo(Result.Success(null))
        }

    private fun interval(id: String, end: Long?) =
        SubTaskInterval(
            subTaskIntervalId = id,
            parentTaskIntervalId = "i1",
            parentSubTaskId = "s1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = end?.let(Instant::fromEpochMilliseconds),
            durationMillis = end ?: 0L,
        )
}
