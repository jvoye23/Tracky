package com.jvcs.tracky.features.project.data.task

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.FakeDeviceIdProvider
import com.jvcs.tracky.core.domain.util.FakeServerClockOffsetStore
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The task-start invariant: a task has at most one open interval, against a real (in-memory)
 * database.
 *
 * The timer itself lives only in memory, so a process death leaves an open interval behind with
 * nothing tracking it. Opening a second one on the next start strands the first, and closing a
 * stranded interval banks every hour since it was opened — the 75-hour day this test exists to
 * prevent.
 */
internal class RoomLocalTaskDataSourceStartTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dataSource: RoomLocalTaskDataSource
    private val timeProvider = FakeTimeProvider()
    private val offsetStore = FakeServerClockOffsetStore()
    private val serverClock = ServerClock(timeProvider, offsetStore)

    @BeforeTest
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<TrackyDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dataSource =
            RoomLocalTaskDataSource(
                db.projectDao,
                db.subTaskIntervalDao,
                db.taskIntervalDao,
                FakeDeviceIdProvider(),
                serverClock,
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
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
                isTimerRunning = false,
                updatedAtEpochMs = null,
            ),
        )
    }

    private suspend fun intervalCount(): Int =
        db.projectDao
            .getTaskWithIntervalsById("t1")
            .first()!!
            .intervals.size

    @Test
    fun startingAnIdleTaskOpensOneInterval() =
        runBlocking {
            seed()
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)

            val result = dataSource.startTask("t1")

            check(result is Result.Success)
            assertThat(intervalCount()).isEqualTo(1)
            assertThat(
                result.data.interval.startDateTimeUtc
                    .toEpochMilliseconds(),
            ).isEqualTo(1_000L)
            // A brand-new row, so it is the caller's job to push it.
            assertThat(result.data.openedInterval).isNotNull()
            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isTrue()
        }

    @Test
    fun startingATaskThatIsAlreadyOpenReusesTheIntervalInsteadOfStackingASecond() =
        runBlocking {
            seed()
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            val first = dataSource.startTask("t1")
            // Three days later, as if the app had been killed and relaunched.
            timeProvider.now = Instant.fromEpochMilliseconds(1_000 + 3 * 24 * 60 * 60 * 1_000L)

            val second = dataSource.startTask("t1")

            check(first is Result.Success && second is Result.Success)
            assertThat(intervalCount()).isEqualTo(1)
            assertThat(second.data.interval.intervalId).isEqualTo(first.data.interval.intervalId)
            // The reused row keeps its original start: inventing a new one would silently discard the
            // time already tracked against it.
            assertThat(
                second.data.interval.startDateTimeUtc
                    .toEpochMilliseconds(),
            ).isEqualTo(1_000L)
            // Nothing new to push: that row is already on the server, or queued for it. Pushing a
            // CREATE again would duplicate it.
            assertThat(second.data.openedInterval).isNull()
            assertThat(db.projectDao.getTaskById("t1")!!.isTimerRunning).isTrue()
        }

    @Test
    fun stoppingClosesTheNewestOpenIntervalWhenAnOlderOneWasLeftBehind() =
        runBlocking {
            seed()
            // A stranded row from an older build, which had no reuse guard.
            db.taskIntervalDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i-stranded",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 0,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                ),
            )
            db.taskIntervalDao.upsertTaskInterval(
                TaskIntervalEntity(
                    intervalId = "i-recent",
                    parentTaskId = "t1",
                    parentProjectId = "p1",
                    startDateTimeEpochMs = 10_000,
                    endDateTimeEpochMs = null,
                    durationMillis = 0,
                ),
            )
            timeProvider.now = Instant.fromEpochMilliseconds(15_000)

            val closed = dataSource.stopTask("t1")

            check(closed is Result.Success)
            checkNotNull(closed.data)
            // Newest-first, so the 5s the user just tracked is banked rather than the whole 15s span
            // of a row nothing was tracking.
            assertThat(closed.data.intervalId).isEqualTo("i-recent")
            assertThat(closed.data.durationMillis).isEqualTo(5_000L)
        }

    // --- clock basis ---------------------------------------------------------------------------

    /**
     * The regression these exist for. An interval's start used to be written from the raw device
     * clock while the tick read it back against the server-corrected one, so the displayed duration
     * carried the whole device-to-server skew: a timer started at zero read 00:00:49 on a phone
     * forty-nine seconds behind the server, on both devices, because the skew was stored in the row.
     */
    @Test
    fun startsTheIntervalOnTheServerCorrectedClock() =
        runBlocking {
            seed()
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            // This phone is 49 seconds behind the server.
            offsetStore.setOffsetMillis(49_000)

            val result = dataSource.startTask("t1")

            check(result is Result.Success)
            assertThat(
                result.data.interval.startDateTimeUtc
                    .toEpochMilliseconds(),
            ).isEqualTo(50_000L)
        }

    @Test
    fun closesTheIntervalOnTheServerCorrectedClockToo() =
        runBlocking {
            seed()
            offsetStore.setOffsetMillis(49_000)
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            dataSource.startTask("t1")
            timeProvider.now = Instant.fromEpochMilliseconds(6_000)

            val closed = dataSource.stopTask("t1")

            check(closed is Result.Success)
            checkNotNull(closed.data)
            assertThat(closed.data.endDateTimeUtc!!.toEpochMilliseconds()).isEqualTo(55_000L)
            // Both ends on one basis, so the offset cancels and the banked figure is the real one.
            assertThat(closed.data.durationMillis).isEqualTo(5_000L)
        }

    /**
     * The offset is re-measured on every pull, so it moves. If it shrinks while a timer runs, the
     * corrected clock steps *backwards*, and the close instant can land before the start instant.
     * The duration is added straight to the task's running total, so an unfloored negative would
     * silently delete time the user really did track.
     */
    @Test
    fun aClockCorrectionThatMovesBackwardsNeverBanksNegativeTime() =
        runBlocking {
            seed()
            offsetStore.setOffsetMillis(49_000)
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            dataSource.startTask("t1")

            // A later pull measures the truth: the phone was right all along, so the correction
            // steps the clock back by the forty-nine seconds it had been adding.
            timeProvider.now = Instant.fromEpochMilliseconds(2_000)
            serverClock.observe(
                serverNow = Instant.fromEpochMilliseconds(2_000),
                receivedAt = Instant.fromEpochMilliseconds(2_000),
            )

            val closed = dataSource.stopTask("t1")

            check(closed is Result.Success)
            checkNotNull(closed.data)
            assertThat(closed.data.durationMillis).isEqualTo(0L)
            assertThat(db.projectDao.getTaskById("t1")!!.durationMillis).isEqualTo(0L)
        }

    /**
     * The composed property, and the thing the user actually sees: under skew, a timer just started
     * reads zero. `TimeManager` renders `elapsedAt(serverClock.now())`, so this is that subtraction.
     */
    @Test
    fun aTimerJustStartedReadsZeroUnderClockSkew() =
        runBlocking {
            seed()
            offsetStore.setOffsetMillis(49_000)
            timeProvider.now = Instant.fromEpochMilliseconds(1_000)
            val result = dataSource.startTask("t1")

            check(result is Result.Success)
            val startedAt = result.data.interval.startDateTimeUtc
            assertThat((serverClock.now() - startedAt).inWholeMilliseconds).isEqualTo(0L)
        }
}
