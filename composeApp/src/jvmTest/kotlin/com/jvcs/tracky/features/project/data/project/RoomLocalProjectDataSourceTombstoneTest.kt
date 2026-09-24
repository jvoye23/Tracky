package com.jvcs.tracky.features.project.data.project

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.jvcs.tracky.core.database.ServerTreeWriter
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.Tombstone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tombstones through `applyDelta`, against a real (in-memory) database.
 *
 * The seam this covers is the one that was missing, and the gap let a live bug through:
 * `ProjectDaoApplyDeltaTest` calls the DAO with id lists **already split by level**, so it proved
 * the deleting half and never the part that decides which level a tombstone names. That part is a
 * string comparison against the wire, and it was wrong for `task` and `sub_task` — every task and
 * subtask deleted on one device stayed on the other, silently, because an unrecognised entityType
 * is deliberately ignored.
 *
 * So the types below are written as **literals, transcribed from backend-delta-sync-api.md §3**,
 * never as constants. A test that says `Tombstone(Tombstone.TASK, …)` passes whatever that constant
 * happens to hold, which is exactly how the bug survived; these strings have to be read off the
 * spec and disagree loudly when the code drifts from it.
 */
internal class RoomLocalProjectDataSourceTombstoneTest {

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
        dataSource = RoomLocalProjectDataSource(db.projectDao, ServerTreeWriter(db))
    }

    @AfterTest
    fun tearDown() = db.close()

    /** One row at every level, each a direct child of the one above. */
    private suspend fun seedTree() {
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
                endDateTimeEpochMs = null,
                durationMillis = 0,
                startedByDeviceId = null,
            ),
        )
        db.projectDao.upsertProjectSubTask(
            ProjectSubTaskEntity(
                projectSubTaskId = "s1",
                parentProjectTaskId = "t1",
                parentProjectId = "p1",
                title = "s",
                description = null,
                durationMillis = 0,
                isTimerRunning = false,
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                isFinished = false,
            ),
        )
        db.subTaskIntervalDao.upsertSubTaskInterval(
            SubTaskIntervalEntity(
                subTaskIntervalId = "si1",
                parentSubTaskId = "s1",
                parentTaskIntervalId = "i1",
                parentProjectId = "p1",
                startDateTimeEpochMs = 0,
                endDateTimeEpochMs = null,
                durationMillis = 0,
            ),
        )
    }

    private suspend fun applyTombstones(vararg tombstones: Tombstone) =
        dataSource.applyDelta(
            SyncChanges(
                cursor = 1,
                serverNow = null,
                fullResyncRequired = false,
                hasMore = false,
                projects = emptyList(),
                tasks = emptyList(),
                taskIntervals = emptyList(),
                subTasks = emptyList(),
                subTaskIntervals = emptyList(),
                tombstones = tombstones.toList(),
            ),
        )

    // --- one test per level, so a failure names the level -------------------------------------

    /** The reported bug: a task deleted on another device must leave this device's database. */
    @Test
    fun aTaskTombstoneDeletesTheTask() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("task", "t1"))

            assertThat(db.taskDao.getTaskById("t1"), name = "the task row survived its tombstone").isNull()
            // Room cascades, so the task's own interval goes with it whether or not the server
            // bothered to name it.
            assertThat(
                db.taskIntervalDao.getIntervalById("i1"),
                name = "the task's interval was left orphaned",
            ).isNull()
        }

    /** The other half of the same bug, and the one easy to forget. */
    @Test
    fun aSubTaskTombstoneDeletesTheSubTask() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("sub_task", "s1"))

            assertThat(db.projectDao.getSubTaskById("s1"), name = "the subtask row survived its tombstone").isNull()
            assertThat(
                db.subTaskIntervalDao.getSubTaskIntervalById("si1"),
                name = "the subtask's interval was left orphaned",
            ).isNull()
        }

    @Test
    fun aProjectTombstoneDeletesTheProject() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("project", "p1"))

            assertThat(db.projectDao.getProjectById("p1")).isNull()
        }

    @Test
    fun aTaskIntervalTombstoneDeletesTheInterval() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("task_interval", "i1"))

            assertThat(db.taskIntervalDao.getIntervalById("i1")).isNull()
            assertThat(
                db.taskDao.getTaskById("t1"),
                name = "deleting an interval must not take its task",
            ).isNotNull()
        }

    @Test
    fun aSubTaskIntervalTombstoneDeletesTheInterval() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("sub_task_interval", "si1"))

            assertThat(db.subTaskIntervalDao.getSubTaskIntervalById("si1")).isNull()
            assertThat(db.projectDao.getSubTaskById("s1")).isNotNull()
        }

    /** A project delete cascades on the server, so all five arrive together. */
    @Test
    fun theCascadeOfAProjectDeleteClearsEveryLevel() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(
                Tombstone("project", "p1"),
                Tombstone("task", "t1"),
                Tombstone("task_interval", "i1"),
                Tombstone("sub_task", "s1"),
                Tombstone("sub_task_interval", "si1"),
            )

            assertThat(db.projectDao.getProjectById("p1")).isNull()
            assertThat(db.taskDao.getTaskById("t1")).isNull()
            assertThat(db.taskIntervalDao.getIntervalById("i1")).isNull()
            assertThat(db.projectDao.getSubTaskById("s1")).isNull()
            assertThat(db.subTaskIntervalDao.getSubTaskIntervalById("si1")).isNull()
        }

    // --- the guard this fix must not trample ---------------------------------------------------

    /**
     * The one way this change could destroy work: a row edited offline is still in the outbox, and
     * the server's tombstone predates the edit it has not heard about yet.
     */
    @Test
    fun aTaskTheOutboxIsStillCarryingOutlivesItsTombstone() =
        runBlocking<Unit> {
            seedTree()
            db.pendingSyncDao.enqueueDeduped(
                PendingSyncEntity(
                    operationId = "op-t1",
                    entityId = "t1",
                    entityType = "project_task",
                    operationType = "UPDATE",
                    createdAtEpochMs = 0,
                    parentEntityId = null,
                ),
            )

            applyTombstones(Tombstone("task", "t1"))

            assertThat(
                db.taskDao.getTaskById("t1"),
                name = "a tombstone destroyed an edit the outbox had not pushed yet",
            ).isNotNull()
        }

    /** A newer server knowing about a level this build does not must not fail the page. */
    @Test
    fun anUnknownEntityTypeIsIgnoredRatherThanFailingTheDelta() =
        runBlocking<Unit> {
            seedTree()

            applyTombstones(Tombstone("something_new_in_v2", "x1"), Tombstone("task", "t1"))

            assertThat(db.taskDao.getTaskById("t1"), name = "one unknown type stopped the rest of the page").isNull()
            assertThat(db.projectDao.getProjectById("p1")).isNotNull()
        }
}
