@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.data.sync.SyncCoordinator
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.serverWinsOnPull
import com.jvcs.tracky.core.domain.sync.serverWinsOnPullForInterval
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.interval.OfflineFirstIntervalRepository
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project.data.subtask.OfflineFirstSubTaskRepository
import com.jvcs.tracky.features.project.data.subtaskinterval.OfflineFirstSubTaskIntervalRepository
import com.jvcs.tracky.features.project.data.task.OfflineFirstTaskRepository
import com.jvcs.tracky.features.project.domain.interval.LocalIntervalDataSource
import com.jvcs.tracky.features.project.domain.interval.RemoteIntervalDataSource
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.RemoteSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import com.jvcs.tracky.features.project.domain.subtaskinterval.LocalSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.RemoteSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.RemoteTaskDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The one store the three local fakes share.
 *
 * Projects, tasks and intervals live in a single database in production, and the cascades between
 * them are what several of these tests are actually about — so the fakes cannot each keep their own
 * private copy. Splitting the repositories does not split the schema.
 */
class FakeDb {
    val projects = linkedMapOf<String, Project>()
    val tasks = linkedMapOf<String, ProjectTask>()
    val intervals = linkedMapOf<String, TaskInterval>()
    val subTasks = linkedMapOf<String, ProjectSubTask>()
    val subTaskIntervals = linkedMapOf<String, SubTaskInterval>()

    val projectsFlow = MutableStateFlow<List<Project>>(emptyList())

    fun emit() { projectsFlow.value = projects.values.toList() }

    /** Room cascades projects -> project_records -> task_intervals; the fake has to as well. */
    fun cascadeDeleteProject(projectId: String) {
        projects.remove(projectId)
        tasks.values.filter { it.parentProjectId == projectId }
            .map { it.projectTaskId }
            .forEach { cascadeDeleteTask(it) }
        emit()
    }

    fun cascadeDeleteTask(taskId: String) {
        tasks.remove(taskId)
        intervals.values.filter { it.parentTaskId == taskId }
            .map { it.intervalId }
            .forEach { cascadeDeleteTaskInterval(it) }
        subTasks.values.filter { it.parentProjectTaskId == taskId }
            .map { it.projectSubTaskId }
            .forEach { cascadeDeleteSubTask(it) }
    }

    fun cascadeDeleteSubTask(subTaskId: String) {
        subTasks.remove(subTaskId)
        subTaskIntervals.values.filter { it.parentSubTaskId == subTaskId }
            .map { it.subTaskIntervalId }
            .forEach { subTaskIntervals.remove(it) }
    }

    /**
     * sub_task_intervals has a second cascading foreign key, onto task_intervals — a subtask
     * interval always sits inside exactly one task interval, so removing the outer row removes it.
     */
    fun cascadeDeleteTaskInterval(intervalId: String) {
        intervals.remove(intervalId)
        subTaskIntervals.values.filter { it.parentTaskIntervalId == intervalId }
            .map { it.subTaskIntervalId }
            .forEach { subTaskIntervals.remove(it) }
    }

    /**
     * Builds a project without storing it — for the offline-create path, where the repository has
     * to see the row as absent to treat the write as a CREATE.
     */
    fun newProject(id: String) = Project(
        projectId = id,
        title = "title-$id",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null
    )

    fun newTask(taskId: String, projectId: String) = ProjectTask(
        projectTaskId = taskId,
        title = "task-$taskId",
        durationMillis = 0,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = projectId,
        isTimerRunning = false,
    )

    /** Stores the row as if it were already there — a project the server and device both know. */
    fun seedProject(id: String) = newProject(id).also { projects[id] = it; emit() }

    fun newSubTask(subTaskId: String, taskId: String, projectId: String) = ProjectSubTask(
        projectSubTaskId = subTaskId,
        parentProjectTaskId = taskId,
        parentProjectId = projectId,
        title = "subtask-$subTaskId",
        durationMillis = 0,
        isTimerRunning = false,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
    )

    fun newSubTaskInterval(
        intervalId: String,
        subTaskId: String,
        taskIntervalId: String,
        projectId: String,
        startedParentTimer: Boolean = false
    ) = SubTaskInterval(
        subTaskIntervalId = intervalId,
        parentTaskIntervalId = taskIntervalId,
        parentSubTaskId = subTaskId,
        parentProjectId = projectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = null,
        durationMillis = 0,
        startedParentTimer = startedParentTimer
    )

    fun seedTask(taskId: String, projectId: String) =
        newTask(taskId, projectId).also { tasks[taskId] = it }

    fun seedSubTask(subTaskId: String, taskId: String, projectId: String) =
        newSubTask(subTaskId, taskId, projectId).also { subTasks[subTaskId] = it }

    fun seedSubTaskInterval(
        intervalId: String,
        subTaskId: String,
        taskIntervalId: String,
        projectId: String,
        startedParentTimer: Boolean = false
    ) = newSubTaskInterval(intervalId, subTaskId, taskIntervalId, projectId, startedParentTimer)
        .also { subTaskIntervals[intervalId] = it }
}

// --- Local data sources ---------------------------------------------------------------------------

class FakeLocalProjectDataSource(private val db: FakeDb = FakeDb()) : LocalProjectDataSource {
    val projects get() = db.projects
    val tasks get() = db.tasks
    val intervals get() = db.intervals

    val upsertedProjectIds = mutableListOf<String>()
    /** One entry per updateSortIndices call, so tests can assert a reorder is a single write. */
    val sortIndexWrites = mutableListOf<Map<String, Long>>()
    val sortIndexWriteTimestamps = mutableListOf<Instant>()
    var failSortIndexWrite = false

    override fun getProjects(): Flow<List<Project>> = db.projectsFlow
    override fun getActiveProjects(): Flow<List<Project>> =
        db.projectsFlow.map { list -> list.filter { !it.isArchived && !it.isFinished && it.trashedAt == null } }
    override fun getArchivedProjects(): Flow<List<Project>> =
        db.projectsFlow.map { list -> list.filter { it.isArchived && it.trashedAt == null } }
    override fun getTrashedProjects(): Flow<List<Project>> =
        db.projectsFlow.map { list -> list.filter { it.trashedAt != null } }

    override suspend fun getPinnedProjects(): Result<List<Project>, DataError.Local> = Result.Success(
        projects.values.filter { it.isPinned && !it.isArchived && !it.isFinished && it.trashedAt == null }
    )

    override suspend fun getExpiredTrashedProjectIds(cutoff: Instant): Result<List<String>, DataError.Local> =
        Result.Success(projects.values.filter { it.trashedAt != null && it.trashedAt!! < cutoff }.map { it.projectId })

    override suspend fun getProjectById(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(projects[projectId])

    override suspend fun getProjectWithTasksByProjectId(projectId: String): Result<Project?, DataError.Local> =
        Result.Success(projects[projectId])

    override suspend fun getSortIndices(): Result<Map<String, Long?>, DataError.Local> =
        Result.Success(projects.mapValues { (_, project) -> project.sortIndex })

    override suspend fun updateSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant
    ): EmptyResult<DataError.Local> {
        // All or nothing, like the DAO transaction it stands in for.
        if (failSortIndexWrite) return Result.Error(DataError.Local.DISK_FULL)
        sortIndexWrites += indices
        sortIndexWriteTimestamps += updatedAt
        indices.forEach { (id, index) ->
            projects[id]?.let { projects[id] = it.copy(sortIndex = index, ownUpdatedAt = updatedAt) }
        }
        db.emit()
        return Result.Success(Unit)
    }

    override suspend fun upsertProject(project: Project): EmptyResult<DataError.Local> {
        upsertedProjectIds += project.projectId
        projects[project.projectId] = project
        db.emit()
        return Result.Success(Unit)
    }

    /**
     * Stands in for ProjectDao.upsertServerTree: writes the whole tree and applies the same merge
     * rules, so a pull in a test can clobber (or refuse to clobber) local state the way it would
     * against a real database.
     */
    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError.Local> {
        projects.forEach { incoming ->
            if (serverWinsOnPull(this.projects[incoming.projectId]?.ownUpdatedAt?.toEpochMilliseconds(),
                    incoming.ownUpdatedAt?.toEpochMilliseconds())) {
                this.projects[incoming.projectId] = incoming
            }
        }
        val incomingTasks = projects.flatMap { it.projectTasks.orEmpty() }
        incomingTasks.forEach { incoming ->
            if (serverWinsOnPull(tasks[incoming.projectTaskId]?.ownUpdatedAt?.toEpochMilliseconds(),
                    incoming.ownUpdatedAt?.toEpochMilliseconds())) {
                tasks[incoming.projectTaskId] = incoming
            }
        }
        incomingTasks.flatMap { it.intervals }.forEach { incoming ->
            val local = intervals[incoming.intervalId]
            if (local == null || serverWinsOnPullForInterval(local.endDateTimeUtc?.toEpochMilliseconds())) {
                intervals[incoming.intervalId] = incoming
            }
        }
        db.emit()
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Local> {
        db.cascadeDeleteProject(projectId)
        return Result.Success(Unit)
    }

    override suspend fun deleteAllProjects(): EmptyResult<DataError.Local> {
        projects.clear(); tasks.clear(); intervals.clear(); db.emit()
        return Result.Success(Unit)
    }
}

class FakeLocalTaskDataSource(private val db: FakeDb = FakeDb()) : LocalTaskDataSource {
    val tasks get() = db.tasks
    val intervals get() = db.intervals

    val upsertedTaskIds = mutableListOf<String>()
    val deletedTaskIds = mutableListOf<String>()
    /** startTask generates the id in production; pin it here so tests can assert on it. */
    var nextIntervalId = "i1"
    var clock: Instant = Instant.fromEpochMilliseconds(10_000)

    // `intervals` on the receiver shadows the db map, so the lookup has to name it explicitly.
    private fun ProjectTask.withIntervals() =
        copy(intervals = db.intervals.values.filter { it.parentTaskId == projectTaskId })

    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> =
        flowOf(tasks[taskId]?.withIntervals())

    override suspend fun getTaskById(taskId: String): Result<ProjectTask?, DataError.Local> =
        Result.Success(tasks[taskId]?.withIntervals())

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError.Local> {
        upsertedTaskIds += projectTask.projectTaskId
        tasks[projectTask.projectTaskId] = projectTask
        return Result.Success(Unit)
    }

    override suspend fun deleteProjectTask(taskId: String): EmptyResult<DataError.Local> {
        deletedTaskIds += taskId
        db.cascadeDeleteTask(taskId)
        return Result.Success(Unit)
    }

    override suspend fun updateTaskDuration(
        taskId: String,
        newDurationMillis: Long
    ): EmptyResult<DataError.Local> {
        tasks[taskId]?.let { tasks[taskId] = it.copy(durationMillis = newDurationMillis) }
        return Result.Success(Unit)
    }

    override suspend fun updateTaskTitle(taskId: String, title: String): EmptyResult<DataError.Local> {
        tasks[taskId]?.let { tasks[taskId] = it.copy(title = title) }
        return Result.Success(Unit)
    }

    override suspend fun startTask(taskId: String): Result<TaskInterval, DataError.Local> {
        // Mirrors the Room implementation: the interval carries its project, so an unknown task
        // has nothing to hang the new row off.
        val task = tasks[taskId] ?: return Result.Error(DataError.Local.NOT_FOUND)
        val interval = TaskInterval(
            intervalId = nextIntervalId,
            parentTaskId = taskId,
            parentProjectId = task.parentProjectId,
            startDateTimeUtc = clock,
            endDateTimeUtc = null,
            durationMillis = 0L,
        )
        intervals[interval.intervalId] = interval
        tasks[taskId] = task.copy(isTimerRunning = true)
        return Result.Success(interval)
    }

    override suspend fun stopTask(taskId: String): Result<TaskInterval?, DataError.Local> {
        val open = intervals.values.firstOrNull { it.parentTaskId == taskId && it.endDateTimeUtc == null }
        tasks[taskId]?.let { tasks[taskId] = it.copy(isTimerRunning = false) }
        if (open == null) return Result.Success(null)
        val closed = open.copy(
            endDateTimeUtc = clock,
            durationMillis = (clock - open.startDateTimeUtc).inWholeMilliseconds,
        )
        intervals[closed.intervalId] = closed
        return Result.Success(closed)
    }
}

class FakeLocalIntervalDataSource(private val db: FakeDb = FakeDb()) : LocalIntervalDataSource {
    val intervals get() = db.intervals

    val upsertedIntervalIds = mutableListOf<String>()
    val deletedIntervalIds = mutableListOf<String>()

    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError.Local> {
        upsertedIntervalIds += interval.intervalId
        intervals[interval.intervalId] = interval
        return Result.Success(Unit)
    }

    override suspend fun getIntervalById(intervalId: String): Result<TaskInterval?, DataError.Local> =
        Result.Success(intervals[intervalId])

    override suspend fun getOpenIntervalByTaskId(taskId: String): Result<TaskInterval?, DataError.Local> =
        Result.Success(intervals.values.firstOrNull { it.parentTaskId == taskId && it.endDateTimeUtc == null })

    override suspend fun deleteTaskInterval(intervalId: String): EmptyResult<DataError.Local> {
        deletedIntervalIds += intervalId
        intervals.remove(intervalId)
        return Result.Success(Unit)
    }
}

// --- Remote data sources --------------------------------------------------------------------------

class FakeRemoteProjectDataSource : RemoteProjectDataSource {
    var failWith: DataError.Remote? = null
    val postedProjects = mutableListOf<Project>()
    val postedProjectIds = mutableListOf<String>()
    val updatedProjectIds = mutableListOf<String>()
    val deletedProjectIds = mutableListOf<String>()
    /** One entry per reorderProjects call, so tests can assert a drag is a single network call. */
    val reorderCalls = mutableListOf<Map<String, Long>>()
    val reorderTimestamps = mutableListOf<Instant>()

    /** What GET /api/projects hands back — the whole tree, tasks and intervals included. */
    var projectsToReturn: List<Project> = emptyList()

    override suspend fun getProjects(): Result<List<Project>, DataError.Remote> =
        failWith?.let { Result.Error(it) } ?: Result.Success(projectsToReturn)

    override suspend fun postProject(project: Project): Result<Project, DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        postedProjects += project
        postedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        updatedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        reorderCalls += indices
        reorderTimestamps += updatedAt
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Remote> {
        failWith?.let { return Result.Error(it) }
        deletedProjectIds += projectId
        return Result.Success(Unit)
    }
}

class FakeRemoteTaskDataSource : RemoteTaskDataSource {
    /** Fails every verb. Per-verb switches below override it, so a test can fail just the POST. */
    var failWith: DataError.Remote? = null
    var postFailWith: DataError.Remote? = null
    var updateFailWith: DataError.Remote? = null
    var getFailWith: DataError.Remote? = null
    val postedTaskIds = mutableListOf<String>()
    val updatedTaskIds = mutableListOf<String>()
    val deletedTaskIds = mutableListOf<String>()
    /** "<projectId>/<taskId>" per call, so tests can assert the route was built from both ids. */
    val taskRoutes = mutableListOf<String>()
    /** What GET /api/projects/{id}/tasks hands back — used by conflict resolution. */
    var tasksToReturn: List<ProjectTask> = emptyList()

    override suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Remote> =
        (getFailWith ?: failWith)?.let { Result.Error(it) } ?: Result.Success(tasksToReturn)

    override suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> {
        taskRoutes += "$projectId/${task.projectTaskId}"
        (postFailWith ?: failWith)?.let { return Result.Error(it) }
        postedTaskIds += task.projectTaskId
        return Result.Success(task)
    }

    override suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Remote> {
        taskRoutes += "$projectId/${task.projectTaskId}"
        (updateFailWith ?: failWith)?.let { return Result.Error(it) }
        updatedTaskIds += task.projectTaskId
        return Result.Success(task)
    }

    override suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Remote> {
        taskRoutes += "$projectId/$taskId"
        failWith?.let { return Result.Error(it) }
        deletedTaskIds += taskId
        return Result.Success(Unit)
    }
}

class FakeRemoteIntervalDataSource : RemoteIntervalDataSource {
    // Separate failure switches per verb so a test can fail one interval call while the others
    // around it still succeed.
    var postFailWith: DataError.Remote? = null
    var updateFailWith: DataError.Remote? = null
    var deleteFailWith: DataError.Remote? = null
    val postedIntervalIds = mutableListOf<String>()
    val updatedIntervalIds = mutableListOf<String>()
    val deletedIntervalIds = mutableListOf<String>()
    /** "<projectId>/<taskId>" per interval call, so tests can assert the route was resolved. */
    val intervalRoutes = mutableListOf<String>()

    override suspend fun postInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote> {
        intervalRoutes += "${interval.parentProjectId}/${interval.parentTaskId}"
        postFailWith?.let { return Result.Error(it) }
        postedIntervalIds += interval.intervalId
        return Result.Success(interval)
    }

    override suspend fun updateInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote> {
        intervalRoutes += "${interval.parentProjectId}/${interval.parentTaskId}"
        updateFailWith?.let { return Result.Error(it) }
        updatedIntervalIds += interval.intervalId
        return Result.Success(interval)
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        intervalRoutes += "$projectId/$taskId"
        deleteFailWith?.let { return Result.Error(it) }
        deletedIntervalIds += intervalId
        return Result.Success(Unit)
    }
}

/**
 * Backed by [FakeDb] rather than by scripted return values, so the cascades a subtask write
 * triggers (a sibling timer closing, a task interval closing with it) are real in tests.
 *
 * [startResult] and [stopResult] stay as overrides for the timer tests, which need to force a
 * specific SubTaskTimerChange without driving the whole start/stop machinery.
 */
class FakeLocalSubTaskDataSource(private val db: FakeDb = FakeDb()) : LocalSubTaskDataSource {
    var startResult: SubTaskTimerChange? = null
    var stopResult: SubTaskTimerChange? = null
    var failReadWith: DataError.Local? = null
    val upserted = mutableListOf<ProjectSubTask>()
    val deleted = mutableListOf<String>()

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> =
        flowOf(db.subTasks.values.filter { it.parentProjectTaskId == taskId })

    override suspend fun getSubTaskById(subTaskId: String): Result<ProjectSubTask?, DataError.Local> {
        failReadWith?.let { return Result.Error(it) }
        return Result.Success(db.subTasks[subTaskId])
    }

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError.Local> {
        upserted += subTask
        db.subTasks[subTask.projectSubTaskId] = subTask
        return Result.Success(Unit)
    }

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError.Local> {
        deleted += subTaskId
        db.cascadeDeleteSubTask(subTaskId)
        return Result.Success(Unit)
    }

    override suspend fun startSubTask(subTaskId: String) = Result.Success(startResult!!)
    override suspend fun stopSubTask(subTaskId: String) = Result.Success(stopResult)
}

class FakeRemoteSubTaskDataSource : RemoteSubTaskDataSource {
    // Separate failure switches per verb, matching FakeRemoteIntervalDataSource.
    var postFailWith: DataError.Remote? = null
    var updateFailWith: DataError.Remote? = null
    var deleteFailWith: DataError.Remote? = null
    /** What a conflict-resolving read sees — the server's copy of the row. */
    var serverSubTasks = listOf<ProjectSubTask>()
    val postedSubTaskIds = mutableListOf<String>()
    val updatedSubTaskIds = mutableListOf<String>()
    val deletedSubTaskIds = mutableListOf<String>()
    /** "<projectId>/<taskId>" per call, so tests can assert the route was resolved. */
    val subTaskRoutes = mutableListOf<String>()

    override suspend fun getSubTasksByTaskId(
        projectId: String,
        taskId: String
    ): Result<List<ProjectSubTask>, DataError.Remote> = Result.Success(serverSubTasks)

    override suspend fun postSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote> {
        subTaskRoutes += "$projectId/$taskId"
        postFailWith?.let { return Result.Error(it) }
        postedSubTaskIds += subTask.projectSubTaskId
        return Result.Success(subTask)
    }

    override suspend fun updateSubTask(
        projectId: String,
        taskId: String,
        subTask: ProjectSubTask
    ): Result<ProjectSubTask, DataError.Remote> {
        subTaskRoutes += "$projectId/$taskId"
        updateFailWith?.let { return Result.Error(it) }
        updatedSubTaskIds += subTask.projectSubTaskId
        return Result.Success(subTask)
    }

    override suspend fun deleteSubTask(
        projectId: String,
        taskId: String,
        subTaskId: String
    ): EmptyResult<DataError.Remote> {
        subTaskRoutes += "$projectId/$taskId"
        deleteFailWith?.let { return Result.Error(it) }
        deletedSubTaskIds += subTaskId
        return Result.Success(Unit)
    }
}

class FakeLocalSubTaskIntervalDataSource(private val db: FakeDb = FakeDb()) : LocalSubTaskIntervalDataSource {
    var failReadWith: DataError.Local? = null
    val upserted = mutableListOf<SubTaskInterval>()

    override suspend fun upsertSubTaskInterval(interval: SubTaskInterval): EmptyResult<DataError.Local> {
        upserted += interval
        db.subTaskIntervals[interval.subTaskIntervalId] = interval
        return Result.Success(Unit)
    }

    override suspend fun getSubTaskIntervalById(
        intervalId: String
    ): Result<SubTaskInterval?, DataError.Local> {
        failReadWith?.let { return Result.Error(it) }
        return Result.Success(db.subTaskIntervals[intervalId])
    }

    override suspend fun getOpenIntervalBySubTaskId(
        subTaskId: String
    ): Result<SubTaskInterval?, DataError.Local> = Result.Success(
        db.subTaskIntervals.values.firstOrNull {
            it.parentSubTaskId == subTaskId && it.endDateTimeUtc == null
        }
    )

    override suspend fun deleteSubTaskInterval(intervalId: String): EmptyResult<DataError.Local> {
        db.subTaskIntervals.remove(intervalId)
        return Result.Success(Unit)
    }
}

class FakeRemoteSubTaskIntervalDataSource : RemoteSubTaskIntervalDataSource {
    // Separate failure switches per verb, matching FakeRemoteIntervalDataSource.
    var postFailWith: DataError.Remote? = null
    var updateFailWith: DataError.Remote? = null
    var deleteFailWith: DataError.Remote? = null
    val postedIntervalIds = mutableListOf<String>()
    val updatedIntervalIds = mutableListOf<String>()
    val deletedIntervalIds = mutableListOf<String>()

    /**
     * "<projectId>/<taskId>/<subTaskId>" per call. The task id is the one the interval itself does
     * not carry, so this is what proves the repository resolved it from the parent subtask.
     */
    val intervalRoutes = mutableListOf<String>()

    override suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote> {
        intervalRoutes += "$projectId/$taskId/${interval.parentSubTaskId}"
        postFailWith?.let { return Result.Error(it) }
        postedIntervalIds += interval.subTaskIntervalId
        // The real source refills parentTaskIntervalId and startedParentTimer from the row it
        // sent, because the wire carries neither — so a correct echo is the input unchanged.
        return Result.Success(interval)
    }

    override suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote> {
        intervalRoutes += "$projectId/$taskId/${interval.parentSubTaskId}"
        updateFailWith?.let { return Result.Error(it) }
        updatedIntervalIds += interval.subTaskIntervalId
        return Result.Success(interval)
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        subTaskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        intervalRoutes += "$projectId/$taskId/$subTaskId"
        deleteFailWith?.let { return Result.Error(it) }
        deletedIntervalIds += intervalId
        return Result.Success(Unit)
    }
}

// --- Queue + scheduler ----------------------------------------------------------------------------

/**
 * In-memory pending-sync queue. Reimplements the DAO's dedup rules rather than delegating, because
 * those rules (a pending CREATE absorbs later UPDATEs; a DELETE cancels a pending CREATE) are part
 * of the behaviour under test.
 */
class FakePendingSyncDataSource : PendingSyncDataSource {
    private val ops = mutableListOf<PendingSyncOperation>()

    fun all(): List<PendingSyncOperation> = ops.sortedBy { it.createdAt }

    override suspend fun getPendingOperations(): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(all())

    override suspend fun getOperationsByEntityId(
        entityId: String
    ): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(ops.filter { it.entityId == entityId }.sortedBy { it.createdAt })

    override suspend fun hasPendingCreate(entityId: String): Result<Boolean, DataError.Local> =
        Result.Success(ops.any { it.entityId == entityId && it.operationType == PendingSyncOperation.OP_CREATE })

    override suspend fun enqueue(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?,
        createdAt: Instant
    ): EmptyResult<DataError.Local> {
        val existing = ops.filter { it.entityId == entityId }
        val op = PendingSyncOperation(
            operationId = Uuid.random().toString(),
            entityId = entityId,
            entityType = entityType,
            operationType = operationType,
            createdAt = createdAt,
            parentEntityId = parentEntityId
        )
        when (operationType) {
            PendingSyncOperation.OP_CREATE -> ops += op
            PendingSyncOperation.OP_UPDATE -> {
                val alreadyQueued = existing.any {
                    it.operationType == PendingSyncOperation.OP_CREATE ||
                        it.operationType == PendingSyncOperation.OP_UPDATE
                }
                if (!alreadyQueued) ops += op
            }
            PendingSyncOperation.OP_DELETE -> {
                val hadPendingCreate = existing.any { it.operationType == PendingSyncOperation.OP_CREATE }
                ops.removeAll { it.entityId == entityId }
                if (!hadPendingCreate) ops += op
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteOperation(operationId: String): EmptyResult<DataError.Local> {
        ops.removeAll { it.operationId == operationId }
        return Result.Success(Unit)
    }

    override suspend fun deleteOperationsByEntityId(entityId: String): EmptyResult<DataError.Local> {
        ops.removeAll { it.entityId == entityId }
        return Result.Success(Unit)
    }
}

class FakeSyncScheduler : SyncScheduler {
    var scheduleCount = 0
    override suspend fun schedulePeriodicSync() { scheduleCount++ }
    override suspend fun schedulePeriodicSyncOnStart() = Unit
    override suspend fun cancelAllSyncs() = Unit
}

// --- Wired fixture --------------------------------------------------------------------------------

/**
 * All three repositories built over one [FakeDb] and one queue, exactly as Koin wires them.
 *
 * The split repositories still collaborate — starting a timer goes task → interval, and the drain
 * order is the whole point of [com.jvcs.tracky.core.data.sync.SyncCoordinator] — so testing any one
 * of them in isolation would mean faking the others and asserting nothing real.
 */
internal class RepoFixture(
    val time: FakeTimeProvider = FakeTimeProvider()
) {
    val db = FakeDb()
    val localProject = FakeLocalProjectDataSource(db)
    val localTask = FakeLocalTaskDataSource(db)
    val localInterval = FakeLocalIntervalDataSource(db)
    val localSubTask = FakeLocalSubTaskDataSource(db)
    val localSubTaskInterval = FakeLocalSubTaskIntervalDataSource(db)
    val remoteProject = FakeRemoteProjectDataSource()
    val remoteTask = FakeRemoteTaskDataSource()
    val remoteInterval = FakeRemoteIntervalDataSource()
    val remoteSubTask = FakeRemoteSubTaskDataSource()
    val remoteSubTaskInterval = FakeRemoteSubTaskIntervalDataSource()
    val queue = FakePendingSyncDataSource()
    val scheduler = FakeSyncScheduler()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    val projectRepository = OfflineFirstProjectRepository(
        localProjectDataSource = localProject,
        remoteProjectDataSource = remoteProject,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = scope,
        timeProvider = time
    )

    val intervalRepository = OfflineFirstIntervalRepository(
        localIntervalDataSource = localInterval,
        remoteIntervalDataSource = remoteInterval,
        localTaskDataSource = localTask,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = scope,
        timeProvider = time
    )

    val taskRepository = OfflineFirstTaskRepository(
        localTaskDataSource = localTask,
        remoteTaskDataSource = remoteTask,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        intervalRepository = intervalRepository,
        timeProvider = time,
        applicationScope = scope
    )

    val subTaskIntervalRepository = OfflineFirstSubTaskIntervalRepository(
        localSubTaskIntervalDataSource = localSubTaskInterval,
        remoteSubTaskIntervalDataSource = remoteSubTaskInterval,
        localSubTaskDataSource = localSubTask,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = scope,
        timeProvider = time
    )

    // Subtasks after tasks: a subtask timer pushes through the interval and task repositories.
    val subTaskRepository = OfflineFirstSubTaskRepository(
        localSubTaskDataSource = localSubTask,
        remoteSubTaskDataSource = remoteSubTask,
        localTaskDataSource = localTask,
        intervalRepository = intervalRepository,
        projectTaskRepository = taskRepository,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = scope,
        timeProvider = time
    )

    val syncCoordinator = SyncCoordinator(
        projectRepository = projectRepository,
        taskRepository = taskRepository,
        intervalRepository = intervalRepository,
        subTaskRepository = subTaskRepository
    )

    /** One project "p1" with one stopped task "t1" under it, both already known to the server. */
    fun seedProjectWithTask(projectId: String = "p1", taskId: String = "t1") {
        db.seedProject(projectId)
        db.seedTask(taskId, projectId)
    }

    /** [seedProjectWithTask] plus one subtask "s1", the whole chain already known to the server. */
    fun seedProjectWithTaskAndSubTask(
        projectId: String = "p1",
        taskId: String = "t1",
        subTaskId: String = "s1"
    ) {
        seedProjectWithTask(projectId, taskId)
        db.seedSubTask(subTaskId, taskId, projectId)
    }
}
