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
import kotlin.time.Clock
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
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        updatedAt = Clock.System.now()
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

    /** Seeds three projects already carrying a contiguous order 0,1,2. */
    private fun FakeLocalProjectDataSource.seedOrderedProjects() {
        projects["p1"] = project("p1").copy(sortIndex = 0)
        projects["p2"] = project("p2").copy(sortIndex = 1)
        projects["p3"] = project("p3").copy(sortIndex = 2)
    }

    @Test
    fun reorderProjects_writesShiftedIndices_inASingleLocalWrite() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource() // online
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        // Move p3 to the middle -> new order p1, p3, p2.
        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        assertTrue(result is Result.Success)
        assertEquals(0L, local.projects["p1"]!!.sortIndex)
        assertEquals(1L, local.projects["p3"]!!.sortIndex)
        assertEquals(2L, local.projects["p2"]!!.sortIndex)
        // The whole gesture is one transaction, carrying only the two cards that actually moved.
        assertEquals(1, local.sortIndexWrites.size)
        assertEquals(mapOf("p3" to 1L, "p2" to 2L), local.sortIndexWrites.single())
    }

    @Test
    fun reorderProjects_makesExactlyOneNetworkCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        repo(local, remote, dao, scheduler).reorderProjects(listOf("p1", "p3", "p2"))

        // One drag must not fan out into one PUT per shifted card.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p3" to 1L, "p2" to 2L), remote.reorderCalls.single())
        assertTrue(remote.updatedProjectIds.isEmpty())
    }

    @Test
    fun reorderProjects_whenNothingMoved_writesNothing() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("p1", "p2", "p3")) // already the stored order

        assertTrue(result is Result.Success)
        assertTrue(local.sortIndexWrites.isEmpty())
        assertTrue(remote.reorderCalls.isEmpty())
    }

    @Test
    fun reorderProjects_whenLocalWriteFails_doesNotHitNetwork_andLeavesOrderUntouched() = runBlocking {
        val local = FakeLocalProjectDataSource().apply { failSortIndexWrite = true }
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        assertTrue(result is Result.Error)
        assertTrue(remote.reorderCalls.isEmpty())
        // Nothing landed: the old order is intact rather than half-applied.
        assertEquals(0L, local.projects["p1"]!!.sortIndex)
        assertEquals(1L, local.projects["p2"]!!.sortIndex)
        assertEquals(2L, local.projects["p3"]!!.sortIndex)
    }

    @Test
    fun reorderProjects_whenOffline_queuesOneOrderOp_andReportsSuccess() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("p1", "p3", "p2"))

        // User sees success because the local write succeeded.
        assertTrue(result is Result.Success)
        assertEquals(1L, local.projects["p3"]!!.sortIndex)

        val ops = dao.getAllPendingOperations()
        assertEquals(1, ops.size)
        assertEquals(PendingSyncEntity.ENTITY_PROJECT_ORDER, ops[0].entityType)
        assertEquals(PendingSyncEntity.OP_UPDATE, ops[0].operationType)
        assertTrue(scheduler.scheduleCount > 0)
    }

    @Test
    fun reorderProjects_twiceWhileOffline_stillQueuesOneOrderOp() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()
        val repository = repo(local, remote, dao, scheduler)

        repository.reorderProjects(listOf("p1", "p3", "p2"))
        repository.reorderProjects(listOf("p3", "p2", "p1"))

        // The order is a single piece of state — two drags collapse into one queued push.
        assertEquals(1, dao.getAllPendingOperations().size)
    }

    @Test
    fun syncPendingOperations_drainsOrderOp_withOneBatchCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Network.NO_INTERNET }
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()
        val repository = repo(local, remote, dao, scheduler)

        repository.reorderProjects(listOf("p1", "p3", "p2")) // queued while offline
        remote.failWith = null                               // back online
        remote.reorderCalls.clear()

        repository.syncPendingOperations()

        assertTrue(dao.getAllPendingOperations().isEmpty())
        // The queued row is rebuilt from current local state: the full order, in one call.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p1" to 0L, "p3" to 1L, "p2" to 2L), remote.reorderCalls.single())
    }

    @Test
    fun reorderProjects_skipsIdsMissingLocally() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedOrderedProjects()

        // "ghost" was deleted on another device but is still in the mirror list the UI committed.
        val result = repo(local, remote, dao, scheduler)
            .reorderProjects(listOf("ghost", "p1", "p2", "p3"))

        assertTrue(result is Result.Success)
        assertFalse(local.sortIndexWrites.single().containsKey("ghost"))
        assertEquals(mapOf("p1" to 1L, "p2" to 2L, "p3" to 3L), local.sortIndexWrites.single())
    }

    /** Pinned section p1,p2 (0,1) and Other section p3,p4,p5 (0,1,2) — both numbered from 0. */
    private fun FakeLocalProjectDataSource.seedTwoSections() {
        projects["p1"] = project("p1").copy(isPinned = true, sortIndex = 0)
        projects["p2"] = project("p2").copy(isPinned = true, sortIndex = 1)
        projects["p3"] = project("p3").copy(sortIndex = 0)
        projects["p4"] = project("p4").copy(sortIndex = 1)
        projects["p5"] = project("p5").copy(sortIndex = 2)
    }

    private fun FakeLocalProjectDataSource.sectionOrder(isPinned: Boolean) =
        projects.values
            .filter { it.isPinned == isPinned }
            .sortedBy { it.sortIndex }
            .map { it.projectId }

    @Test
    fun setProjectsPinned_putsPinnedProjectOnTopOfItsNewSection() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        // p4 sits at index 1 in Other, where p2 already sits at 1 in Pinned. Flipping the flag alone
        // would leave them sharing an index and let the creation date decide the order.
        val result = repo(local, remote, dao, scheduler).setProjectsPinned(listOf("p4"), isPinned = true)

        assertTrue(result is Result.Success)
        assertTrue(local.projects["p4"]!!.isPinned)
        assertEquals(listOf("p4", "p1", "p2"), local.sectionOrder(isPinned = true))
        assertEquals(listOf(0L, 1L, 2L), listOf("p4", "p1", "p2").map { local.projects[it]!!.sortIndex })
    }

    @Test
    fun setProjectsPinned_keepsRelativeOrder_whenPinningSeveralAtOnce() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        // Selection order is a Set's, so the repository must fall back on the stored order: p3 (0)
        // before p5 (2).
        repo(local, remote, dao, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

        assertEquals(listOf("p3", "p5", "p1", "p2"), local.sectionOrder(isPinned = true))
    }

    @Test
    fun setProjectsPinned_unpinning_putsProjectOnTopOfOther() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        repo(local, remote, dao, scheduler).setProjectsPinned(listOf("p1"), isPinned = false)

        assertFalse(local.projects["p1"]!!.isPinned)
        assertEquals(listOf("p1", "p3", "p4", "p5"), local.sectionOrder(isPinned = false))
        assertEquals(listOf("p2"), local.sectionOrder(isPinned = true))
    }

    @Test
    fun setProjectsPinned_reindexesTheSectionInOneBatchCall() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        repo(local, remote, dao, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

        // One gesture, one /sort request — not one per shifted card. p3 already sat at 0 and stays
        // there, so it is not part of the write.
        assertEquals(1, remote.reorderCalls.size)
        assertEquals(mapOf("p5" to 1L, "p1" to 2L, "p2" to 3L), remote.reorderCalls.single())
        assertEquals(1, local.sortIndexWrites.size)
    }

    @Test
    fun setProjectsPinned_skipsIdsMissingLocally() = runBlocking {
        val local = FakeLocalProjectDataSource()
        val remote = FakeRemoteProjectDataSource()
        val dao = FakePendingSyncDao()
        val scheduler = FakeSyncScheduler()
        local.seedTwoSections()

        val result = repo(local, remote, dao, scheduler)
            .setProjectsPinned(listOf("ghost"), isPinned = true)

        assertTrue(result is Result.Success)
        assertEquals(listOf("p1", "p2"), local.sectionOrder(isPinned = true))
        assertTrue(remote.reorderCalls.isEmpty())
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
    /** One entry per updateSortIndices call, so tests can assert a reorder is a single write. */
    val sortIndexWrites = mutableListOf<Map<String, Long>>()
    var failSortIndexWrite = false
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

    override suspend fun getSortIndices(): Map<String, Long?> =
        projects.mapValues { (_, project) -> project.sortIndex }

    override suspend fun updateSortIndices(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError> {
        // All or nothing, like the DAO transaction it stands in for.
        if (failSortIndexWrite) return Result.Error(DataError.Local.DISK_FULL)
        sortIndexWrites += indices
        indices.forEach { (id, index) ->
            projects[id]?.let { projects[id] = it.copy(sortIndex = index, updatedAt = updatedAt) }
        }
        emit()
        return Result.Success(Unit)
    }

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
    val updatedProjectIds = mutableListOf<String>()
    val deletedProjectIds = mutableListOf<String>()
    /** One entry per reorderProjects call, so tests can assert a drag is a single network call. */
    val reorderCalls = mutableListOf<Map<String, Long>>()

    override suspend fun getProjects(): Result<List<Project>, DataError.Network> =
        failWith?.let { Result.Error(it) } ?: Result.Success(emptyList())

    override suspend fun postProject(project: Project): Result<Project, DataError.Network> {
        failWith?.let { return Result.Error(it) }
        postedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun updateProject(project: Project): Result<Project, DataError.Network> {
        failWith?.let { return Result.Error(it) }
        updatedProjectIds += project.projectId
        return Result.Success(project)
    }

    override suspend fun reorderProjects(indices: Map<String, Long>, updatedAt: Instant): EmptyResult<DataError.Network> {
        failWith?.let { return Result.Error(it) }
        reorderCalls += indices
        return Result.Success(Unit)
    }

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
