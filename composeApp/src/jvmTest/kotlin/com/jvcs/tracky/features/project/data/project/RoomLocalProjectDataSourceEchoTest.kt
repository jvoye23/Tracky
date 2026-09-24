package com.jvcs.tracky.features.project.data.project

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.jvcs.tracky.core.database.ServerTreeWriter
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * `applyTimerEcho` against a real (in-memory) database.
 *
 * An active-timer call answers with every interval it touched, and this is where those rows land.
 * It goes through the same merge a pull uses, so the cases worth pinning are the ones that merge
 * exists for — and the one it must never do, which is delete.
 */
internal class RoomLocalProjectDataSourceEchoTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dataSource: RoomLocalProjectDataSource

    @BeforeTest
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dataSource = RoomLocalProjectDataSource(db.projectDao, db.projectTreeDao, db.sortOrderDao, ServerTreeWriter(db))
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun seed(intervalEnd: Long? = null, device: String? = null) {
        db.projectDao.upsertProject(
            ProjectEntity(
                projectId = "p1",
                title = "p",
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
                updatedAtEpochMs = 0,
                sortIndex = null,
            ),
        )
        db.taskDao.upsertProjectTask(
            ProjectTaskEntity(
                projectTaskId = "t1",
                parentProjectId = "p1",
                title = "t",
                description = null,
                durationMillis = 0,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                updatedAtEpochMs = 0,
            ),
        )
        db.taskIntervalDao.upsertTaskInterval(
            TaskIntervalEntity(
                intervalId = "i1",
                parentTaskId = "t1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = intervalEnd,
                durationMillis = intervalEnd ?: 0,
                startedByDeviceId = device,
            ),
        )
    }

    private fun echoed(end: Long?, device: String?) =
        TaskInterval(
            intervalId = "i1",
            parentTaskId = "t1",
            parentProjectId = "p1",
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            endDateTimeUtc = end?.let { Instant.fromEpochMilliseconds(it) },
            durationMillis = end ?: 0,
            startedByDeviceId = device,
        )

    @Test
    fun closesAnIntervalThisDeviceStillHasOpen() =
        runBlocking {
            // The cross-device stop: the user ended this session on their tablet, and the echo is how
            // this device learns about it without waiting for the next pull.
            seed(intervalEnd = null, device = FakeDeviceIdProvider.OTHER_DEVICE)

            dataSource.applyTimerEcho(
                taskIntervals = listOf(echoed(end = 60_000, device = FakeDeviceIdProvider.OTHER_DEVICE)),
                subTaskIntervals = emptyList(),
            )

            assertThat(db.taskIntervalDao.getIntervalById("i1")?.endDateTimeEpochMs).isEqualTo(60_000L)
        }

    @Test
    fun doesNotReopenAnIntervalThisDeviceAlreadyClosed() =
        runBlocking {
            // The server's copy is a pre-stop snapshot when our stop has not drained yet. Taking it
            // would discard the banked duration and then push the reopened row back.
            seed(intervalEnd = 60_000)

            dataSource.applyTimerEcho(
                taskIntervals = listOf(echoed(end = null, device = null)),
                subTaskIntervals = emptyList(),
            )

            assertThat(db.taskIntervalDao.getIntervalById("i1")?.endDateTimeEpochMs).isEqualTo(60_000L)
        }

    @Test
    fun keepsTheProvenanceOfARowTheServerHasNoDeviceIdFor() =
        runBlocking {
            // Every row written before the column existed comes back null; blanking it would make this
            // device's own interval look foreign.
            seed(intervalEnd = null, device = FakeDeviceIdProvider.THIS_DEVICE)

            dataSource.applyTimerEcho(
                taskIntervals = listOf(echoed(end = 60_000, device = null)),
                subTaskIntervals = emptyList(),
            )

            assertThat(
                db.taskIntervalDao.getIntervalById("i1")?.startedByDeviceId,
            ).isEqualTo(FakeDeviceIdProvider.THIS_DEVICE)
        }

    @Test
    fun neverDeletesARowTheEchoDidNotMention() =
        runBlocking {
            // An echo names what the server changed, never what it removed. Only a tombstone deletes,
            // and those arrive on the change feed.
            seed(intervalEnd = 1_000)

            dataSource.applyTimerEcho(taskIntervals = emptyList(), subTaskIntervals = emptyList())

            assertThat(db.taskIntervalDao.getIntervalById("i1")).isNotNull()
            assertThat(db.taskDao.getTaskById("t1")).isNotNull()
            // Trailing Unit: JUnit rejects a whole class whose runBlocking test ends on a
            // value-returning assertion, and reports it as initializationError rather than a failure.
            Unit
        }

    @Test
    fun skipsARowWhoseParentThisDeviceDoesNotHave() =
        runBlocking {
            // Room enforces the foreign keys, and one dangling reference throws inside the transaction
            // — which would lose the rows that were fine along with it.
            seed()

            dataSource.applyTimerEcho(
                taskIntervals =
                    listOf(
                        echoed(end = 60_000, device = null).copy(
                            intervalId = "i-orphan",
                            parentTaskId = "t-unknown",
                        ),
                    ),
                subTaskIntervals = emptyList(),
            )

            assertThat(db.taskIntervalDao.getIntervalById("i-orphan")).isNull()
        }
}
