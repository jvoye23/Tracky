@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.project_tracker.data

import com.jvcs.tracky.core.database.dao.PendingSyncDao
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.model.Project
import com.jvcs.tracky.core.domain.model.ProjectTask
import com.jvcs.tracky.core.domain.model.TaskInterval
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project_tracker.domain.LocalProjectDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class OfflineFirstProjectRepositoryTest {

    private fun repo(
        local: FakeLocalProjectDataSource,
        remote: FakeRemoteProjectDataSource,
        dao: FakePendingSyncDao,
        scheduler: FakeSyncScheduler
    ) = OfflineFirstProjectRepository(
        localProjectDataSource = local,
        remoteProjectDataSource = remote,
        pendingSyncDao = dao,
        syncScheduler = scheduler,
        applicationScope = CoroutineScope(Dispatchers.Unconfined)
    )

    private fun project(id: String) = Project(
        projectId = id,
        title = "title-$id",
        description = null,
        colorArgb = null,
        totalDurationMillis = null,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        isFinished = false,
        endDateTimeUtc = null
    )

    @Test
    fun upsertProject_queuesCreate_whenRemoteOffline() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()

        val result = repo(local, remote, dao, scheduler).upsertProject(project("p1"))

        // User sees success because the local write succeeded.
        assertTrue(result is Result.Success)
        assertNotNull(local.projects["p1"])

        val ops = dao.getAllPendingOperations()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncEntity.ENTITY_PROJECT, ops[0].entityType)
        assertEquals(PendingSyncEntity.OP_CREATE, ops[0].operationType)
        assertTrue(scheduler.scheduleCount > 0)
    }

    @Test
    fun syncPendingOperations_pushesQueuedCreate_andClearsQueue() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        val repository = repo(local, remote, dao, scheduler)

        repository.upsertProject(project("p1")) // queued while offline
        remote.failWith = null                  // back online

        repository.syncPendingOperations()

        assertTrue(dao.getAllPendingOperations().isEmpty())
        assertTrue(remote.postedProjectIds.contains("p1"))
    }

    @Test
    fun reorderProjects_writesNewSortIndex_onlyForShiftedProjects() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource() // online
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        // Seed three projects already carrying a contiguous order 0,1,2.
        local.projects["p1"] = project("p1").copy(sortIndex = 0)
        local.projects["p2"] = project("p2").copy(sortIndex = 1)
        local.projects["p3"] = project("p3").copy(sortIndex = 2)

        // Move p3 to the middle -> new order p1, p3, p2.
        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        assertTrue(result is Result.Success)
        assertEquals(0L, local.projects["p1"]!!.sortIndex)
        assertEquals(1L, local.projects["p3"]!!.sortIndex)
        assertEquals(2L, local.projects["p2"]!!.sortIndex)
        // p1 kept index 0, so it must not have been rewritten; only p3 and p2 shifted.
        assertFalse(local.upsertedProjectIds.contains("p1"))
        assertTrue(local.upsertedProjectIds.containsAll(listOf("p2", "p3")))
    }

    @Test
    fun deleteProject_droppedLocally_whenStillPendingCreate_neverHitsServer() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        val repository = repo(local, remote, dao, scheduler)

        repository.upsertProject(project("p1")) // queued CREATE (never reached server)
        repository.deleteProject("p1")

        assertNull(local.projects["p1"])
        assertTrue(dao.getAllPendingOperations().isEmpty())
        assertFalse(remote.deletedProjectIds.contains("p1"))
    }
}

// --- Fakes ----------------------------------------------------------------------------------------

private class FakeLocalProjectDataSource : LocalProjectDataSource {
    val projects = linkedMapOf<String, Project>()
    val upsertedProjectIds = mutableListOf<String>()
    private val projectsFlow = MutableStateFlow<List<Project>>(emptyList())

    private fun emit() { projectsFlow.value = projects.values.toList() }

    override fun getProjects(): Flow<List<Project>> = projectsFlow
    override fun getArchivedProjects(): Flow<List<Project>> =
        projectsFlow.map { list -> list.filter { it.isArchived && it.trashedAt == null } }
    override fun getTrashedProjects(): Flow<List<Project>> =
        projectsFlow.map { list -> list.filter { it.trashedAt != null } }
    override suspend fun getExpiredTrashedProjectIds(cutoff: Instant): List<String> =
        projects.values.filter { it.trashedAt != null && it.trashedAt!! < cutoff }.map { it.projectId }
    override suspend fun getProjectById(projectId: String): Project? = projects[projectId]
    override suspend fun getProjectWithTasksByProjectId(projectId: String): Project? = projects[projectId]

    override suspend fun upsertProject(project: Project): EmptyResult<DataError> {
        upsertedProjectIds += project.projectId
        projects[project.projectId] = project; emit(); return Result.Success(Unit)
    }

    override suspend fun upsertProjects(projects: List<Project>): EmptyResult<DataError> {
        projects.forEach { this.projects[it.projectId] = it }; emit(); return Result.Success(Unit)
    }

    override suspend fun upsertProjectTask(projectTask: ProjectTask): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteProject(projectId: String) { projects.remove(projectId); emit() }
    override suspend fun deleteProjectTask(taskId: String) = Unit
    override suspend fun deleteAllProjects() { projects.clear(); emit() }
    override suspend fun updateTaskDuration(taskId: String, newDurationMillis: Long) = Unit
    override fun getTaskWithIntervalsById(taskId: String): Flow<ProjectTask?> = flowOf(null)
    override suspend fun upsertTaskInterval(interval: TaskInterval): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun getOpenIntervalByTaskId(taskId: String): TaskInterval? = null
    override suspend fun startTask(taskId: String) = Unit
    override suspend fun stopTask(taskId: String) = Unit
    override suspend fun updateTaskTitle(taskId: String, title: String) = Unit
}

private class FakeRemoteProjectDataSource : RemoteProjectDataSource {
    var failWith: DataError.Network? = null
    val postedProjectIds = mutableListOf<String>()
    val deletedProjectIds = mutableListOf<String>()

    override suspend fun getProjects(): Result<List<Project>, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(emptyList())

    override suspend fun postProject(project: Project): Result<Project, DataError.Network> {
        failWith?.let { return Result.Error(it) }
        postedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(project)

    override suspend fun deleteProject(projectId: String): EmptyResult<DataError.Network> {
        failWith?.let { return Result.Error(it) }
        deletedProjectIds += projectId
        return Result.Success(Unit)
    }

    override suspend fun getTasksByProjectId(projectId: String): Result<List<ProjectTask>, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(emptyList())

    override suspend fun postTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(task)

    override suspend fun updateTaskByProjectId(projectId: String, task: ProjectTask): Result<ProjectTask, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(task)

    override suspend fun deleteTask(projectId: String, taskId: String): EmptyResult<DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(Unit)
}

private class FakePendingSyncDao : PendingSyncDao {
    private val ops = mutableListOf<PendingSyncEntity>()

    override suspend fun upsertOperation(operation: PendingSyncEntity) {
        ops.removeAll { it.operationId == operation.operationId }
        ops += operation
    }

    override fun getPendingOperations(): Flow<List<PendingSyncEntity>> = flowOf(ops.toList())
    override suspend fun getAllPendingOperations(): List<PendingSyncEntity> = ops.sortedBy { it.createdAtEpochMs }
    override suspend fun getOperationsByEntityId(entityId: String): List<PendingSyncEntity> =
        ops.filter { it.entityId == entityId }.sortedBy { it.createdAtEpochMs }
    override suspend fun deleteOperation(operationId: String) { ops.removeAll { it.operationId == operationId } }
    override suspend fun deleteOperationsByEntityId(entityId: String) { ops.removeAll { it.entityId == entityId } }
    override suspend fun clear() { ops.clear() }
}

private class FakeSyncScheduler : SyncScheduler {
    var scheduleCount = 0
    override suspend fun schedulePeriodicSync() { scheduleCount++ }
    override suspend fun schedulePeriodicSyncOnStart() = Unit
    override suspend fun cancelAllSyncs() = Unit
}
