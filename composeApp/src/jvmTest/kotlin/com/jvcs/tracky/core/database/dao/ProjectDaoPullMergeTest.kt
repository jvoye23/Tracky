package com.jvcs.tracky.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [ProjectDao.upsertServerTree] against a real (in-memory) database.
 *
 * The repository-level tests run against a fake that reimplements the same merge rules, so this is
 * the only place the production transaction itself — the flattening, the per-row decisions and the
 * fact that nothing gets deleted — is actually executed.
 */
class ProjectDaoPullMergeTest {

    private lateinit var db: TrackyDatabase
    private lateinit var dao: ProjectDao

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<TrackyDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.projectDao
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun projectEntity(id: String, updatedAt: Long?) = ProjectEntity(
        projectId = id,
        title = "title-$id",
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
        updatedAtEpochMs = updatedAt,
        sortIndex = null,
    )

    private fun taskEntity(id: String, projectId: String, title: String, updatedAt: Long?) = ProjectTaskEntity(
        projectTaskId = id,
        parentProjectId = projectId,
        title = title,
        description = null,
        durationMillis = 0,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = null,
        isFinished = false,
        isTimerRunning = false,
        updatedAtEpochMs = updatedAt,
    )

    private fun intervalEntity(
        id: String,
        taskId: String,
        end: Long?,
        projectId: String = "p1",
    ) = TaskIntervalEntity(
        intervalId = id,
        parentTaskId = taskId,
        parentProjectId = projectId,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = end,
        durationMillis = end ?: 0L,
    )

    @Test
    fun writesTheWholeTree() = runBlocking {
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        assertNotNull(dao.getProjectById("p1"))
        assertNotNull(dao.getTaskById("t1"))
        // Intervals are the whole point: this is what a fresh install could not recover before.
        assertEquals(60_000L, dao.getIntervalById("i1")?.durationMillis)
    }

    /** project_tasks has a CASCADE foreign key onto projects, so the parent must exist first. */
    /** Queues an outbox row, which is what makes a local interval defend itself against a pull. */
    private suspend fun queuePush(intervalId: String, entityType: String = "task_interval") {
        db.pendingSyncDao.enqueueDeduped(
            PendingSyncEntity(
                operationId = "op-$intervalId",
                entityId = intervalId,
                entityType = entityType,
                operationType = "UPDATE",
                createdAtEpochMs = 0,
                parentEntityId = null
            )
        )
    }

    private suspend fun seedProject(id: String = "p1") {
        dao.upsertProject(projectEntity(id, updatedAt = 0))
    }

    /** task_intervals cascades from both project_tasks and projects, so seed the whole chain. */
    private suspend fun seedTask(taskId: String = "t1", projectId: String = "p1") {
        seedProject(projectId)
        dao.upsertProjectTask(taskEntity(taskId, projectId, "seeded", updatedAt = 0))
    }

    @Test
    fun keepsALocalTaskThatIsNewerThanTheServer() = runBlocking {
        seedProject()
        dao.upsertProjectTask(taskEntity("t1", "p1", "edited offline", updatedAt = 500))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = listOf(taskEntity("t1", "p1", "stale server copy", updatedAt = 100)),
            intervals = emptyList(),
        )

        assertEquals("edited offline", dao.getTaskById("t1")?.title)
    }

    @Test
    fun takesTheServerTaskWhenItIsNewer() = runBlocking {
        seedProject()
        dao.upsertProjectTask(taskEntity("t1", "p1", "old local copy", updatedAt = 100))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = listOf(taskEntity("t1", "p1", "fresh from server", updatedAt = 500)),
            intervals = emptyList(),
        )

        assertEquals("fresh from server", dao.getTaskById("t1")?.title)
    }

    @Test
    fun closesAnIntervalTheServerSaysWasStoppedElsewhere() = runBlocking {
        seedTask()
        dao.upsertTaskInterval(intervalEntity("i1", "t1", end = null)) // still ticking here

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        // The user stopped it on their other device. Nothing is queued here, so this device has
        // no unsent change to defend and the server is canonical.
        assertEquals(60_000L, dao.getIntervalById("i1")?.endDateTimeEpochMs)
    }

    @Test
    fun doesNotCloseAnIntervalWhoseOwnChangeIsStillQueued() = runBlocking {
        seedTask()
        dao.upsertTaskInterval(intervalEntity("i1", "t1", end = null))
        queuePush("i1")

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        assertNull(dao.getIntervalById("i1")?.endDateTimeEpochMs)
    }

    @Test
    fun doesNotReopenAClosedIntervalTheServerStillHasOpen() = runBlocking {
        seedTask()
        dao.upsertTaskInterval(intervalEntity("i1", "t1", end = 60_000))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(intervalEntity("i1", "t1", end = null)),
        )

        // Reopening would discard the banked duration; a server copy still open is just the
        // server not having heard the stop yet.
        assertEquals(60_000L, dao.getIntervalById("i1")?.endDateTimeEpochMs)
    }

    @Test
    fun keepsTheLocalDeviceIdWhenTheServerCloseWins() = runBlocking {
        seedTask()
        dao.upsertTaskInterval(
            intervalEntity("i1", "t1", end = null).copy(startedByDeviceId = "device-a")
        )

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(intervalEntity("i1", "t1", end = 60_000)),
        )

        // Every row written before the column existed comes back with a null, so a blind
        // overwrite would blank it and the next start-up would read this device's own rows as
        // foreign.
        assertEquals("device-a", dao.getIntervalById("i1")?.startedByDeviceId)
    }

    @Test
    fun takesTheServersDeviceIdForAnIntervalItHasNeverSeen() = runBlocking {
        seedTask()

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = listOf(
                intervalEntity("i-foreign", "t1", end = null)
                    .copy(startedByDeviceId = "device-b")
            ),
        )

        // The timer another device is running right now. Storing null instead would read as "this
        // device", so StrandedTimerReconciler would park a live timer and ask the user to reclaim
        // it, and phase 2's isForeign would be false for every foreign timer.
        assertEquals("device-b", dao.getIntervalById("i-foreign")?.startedByDeviceId)
    }

    @Test
    fun leavesRowsTheServerDoesNotKnowAbout() = runBlocking {
        seedProject()
        dao.upsertProjectTask(taskEntity("local-only", "p1", "created offline", updatedAt = null))
        dao.upsertTaskInterval(intervalEntity("i-local", "local-only", end = 1_000))

        dao.upsertServerTree(projects = emptyList(), tasks = emptyList(), intervals = emptyList())

        assertNotNull(dao.getTaskById("local-only"))
        assertNotNull(dao.getIntervalById("i-local"))
        Unit
    }

    private fun subTaskEntity(
        id: String,
        taskId: String,
        title: String,
        updatedAt: Long?,
        projectId: String = "p1",
    ) = ProjectSubTaskEntity(
        projectSubTaskId = id,
        parentProjectTaskId = taskId,
        parentProjectId = projectId,
        title = title,
        description = null,
        durationMillis = 0,
        isTimerRunning = false,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = null,
        isFinished = false,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun writesSubTasksNestedTwoLevelsDown() = runBlocking {
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "from server", updatedAt = 100)),
        )

        assertEquals("from server", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun keepsALocalSubTaskThatIsNewerThanTheServer() = runBlocking {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("s1", "t1", "edited offline", updatedAt = 500))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "stale server copy", updatedAt = 100)),
        )

        assertEquals("edited offline", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun takesTheServerSubTaskWhenItIsNewer() = runBlocking {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("s1", "t1", "old local copy", updatedAt = 100))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = listOf(subTaskEntity("s1", "t1", "renamed elsewhere", updatedAt = 900)),
        )

        assertEquals("renamed elsewhere", dao.getSubTaskById("s1")?.title)
    }

    @Test
    fun neverDeletesALocalOnlySubTask() = runBlocking<Unit> {
        seedTask()
        dao.upsertProjectSubTask(subTaskEntity("local-only", "t1", "created offline", updatedAt = null))

        dao.upsertServerTree(
            projects = emptyList(),
            tasks = emptyList(),
            intervals = emptyList(),
            subTasks = emptyList(),
        )

        // Still queued for upload — a pull must never delete it.
        assertNotNull(dao.getSubTaskById("local-only"))
    }

    @Test
    fun skipsAnOrphanSubTaskInsteadOfLosingTheWholePull() = runBlocking<Unit> {
        // project_sub_tasks has a CASCADE foreign key onto project_tasks. Without the filter this
        // row throws inside the transaction and takes the project and task rows down with it — the
        // whole pull, not just the bad row.
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = emptyList(),
            subTasks = listOf(
                subTaskEntity("orphan", "missing-task", "no parent here", updatedAt = 100),
                subTaskEntity("s1", "t1", "fine", updatedAt = 100),
            ),
        )

        assertNull(dao.getSubTaskById("orphan"))
        // Everything around it survived.
        assertNotNull(dao.getProjectById("p1"))
        assertNotNull(dao.getTaskById("t1"))
        assertNotNull(dao.getSubTaskById("s1"))
    }

    private fun subTaskIntervalEntity(
        id: String,
        subTaskId: String,
        taskIntervalId: String,
        end: Long?,
        startedParentTimer: Boolean = false,
        projectId: String = "p1",
    ) = SubTaskIntervalEntity(
        subTaskIntervalId = id,
        parentSubTaskId = subTaskId,
        parentTaskIntervalId = taskIntervalId,
        parentProjectId = projectId,
        startDateTimeEpochMs = 0,
        endDateTimeEpochMs = end,
        durationMillis = end ?: 0L,
        startedParentTimer = startedParentTimer,
    )

    /** sub_task_intervals cascades from project_sub_tasks AND task_intervals — seed both. */
    private suspend fun seedSubTask(subTaskId: String = "s1", taskId: String = "t1") {
        seedTask(taskId)
        dao.upsertProjectSubTask(subTaskEntity(subTaskId, taskId, "seeded", updatedAt = 0))
        dao.upsertTaskInterval(intervalEntity("ti1", taskId, end = null))
    }

    @Test
    fun writesSubTaskIntervalsNestedThreeLevelsDown() = runBlocking {
        dao.upsertServerTree(
            projects = listOf(projectEntity("p1", updatedAt = 100)),
            tasks = listOf(taskEntity("t1", "p1", "from server", updatedAt = 100)),
            intervals = listOf(intervalEntity("ti1", "t1", end = 60_000)),
            subTasks = listOf(subTaskEntity("s1", "t1", "from server", updatedAt = 100)),
            subTaskIntervals = listOf(subTaskIntervalEntity("si1", "s1", "ti1", end = 600)),
        )

        // The last thing a fresh install could not recover.
        assertEquals(600L, dao.getSubTaskIntervalById("si1")?.durationMillis)
    }

    @Test
    fun preservesTheLocalStartedParentTimerFlagAcrossAPull() = runBlocking {
        seedSubTask()
        dao.upsertSubTaskInterval(
            subTaskIntervalEntity("si1", "s1", "ti1", end = 600, startedParentTimer = true)
        )

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(),
            // The server has no column for the flag, so its copy always reads false.
            subTaskIntervals = listOf(subTaskIntervalEntity("si1", "s1", "ti1", end = 900)),
        )

        // Losing this would break "stopping this subtask also stops its parent task".
        assertEquals(true, dao.getSubTaskIntervalById("si1")?.startedParentTimer)
        assertEquals(900L, dao.getSubTaskIntervalById("si1")?.endDateTimeEpochMs)
    }

    @Test
    fun closesASubTaskIntervalTheServerSaysWasStoppedElsewhere() = runBlocking {
        seedSubTask()
        dao.upsertSubTaskInterval(subTaskIntervalEntity("si1", "s1", "ti1", end = null))

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(),
            subTaskIntervals = listOf(subTaskIntervalEntity("si1", "s1", "ti1", end = 60_000)),
        )

        assertEquals(60_000L, dao.getSubTaskIntervalById("si1")?.endDateTimeEpochMs)
    }

    @Test
    fun doesNotCloseASubTaskIntervalWhoseOwnChangeIsStillQueued() = runBlocking {
        seedSubTask()
        dao.upsertSubTaskInterval(subTaskIntervalEntity("si1", "s1", "ti1", end = null))
        queuePush("si1", entityType = "sub_task_interval")

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(),
            subTaskIntervals = listOf(subTaskIntervalEntity("si1", "s1", "ti1", end = 60_000)),
        )

        assertNull(dao.getSubTaskIntervalById("si1")?.endDateTimeEpochMs)
    }

    @Test
    fun aQueuedTaskIntervalDoesNotShieldAnUnrelatedSubTaskInterval() = runBlocking {
        seedSubTask()
        dao.upsertSubTaskInterval(subTaskIntervalEntity("si1", "s1", "ti1", end = null))
        // Same id space, different level: the guard is per id, and these must not collide.
        queuePush("unrelated")

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(),
            subTaskIntervals = listOf(subTaskIntervalEntity("si1", "s1", "ti1", end = 60_000)),
        )

        assertEquals(60_000L, dao.getSubTaskIntervalById("si1")?.endDateTimeEpochMs)
    }

    @Test
    fun skipsASubTaskIntervalWithEitherParentMissing() = runBlocking<Unit> {
        seedSubTask()

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(),
            subTaskIntervals = listOf(
                subTaskIntervalEntity("no-subtask", "missing-subtask", "ti1", end = 1),
                subTaskIntervalEntity("no-interval", "s1", "missing-interval", end = 1),
                subTaskIntervalEntity("fine", "s1", "ti1", end = 1),
            ),
        )

        // Either dangling reference would throw inside the transaction and lose the whole pull.
        assertNull(dao.getSubTaskIntervalById("no-subtask"))
        assertNull(dao.getSubTaskIntervalById("no-interval"))
        assertNotNull(dao.getSubTaskIntervalById("fine"))
    }

    @Test
    fun neverDeletesALocalOnlySubTaskInterval() = runBlocking<Unit> {
        seedSubTask()
        dao.upsertSubTaskInterval(subTaskIntervalEntity("local-only", "s1", "ti1", end = 1))

        dao.upsertServerTree(
            projects = emptyList(), tasks = emptyList(), intervals = emptyList(),
            subTasks = emptyList(), subTaskIntervals = emptyList(),
        )

        assertNotNull(dao.getSubTaskIntervalById("local-only"))
    }
}
