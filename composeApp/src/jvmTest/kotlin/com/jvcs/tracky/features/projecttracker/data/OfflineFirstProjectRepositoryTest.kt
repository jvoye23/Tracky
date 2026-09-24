@file:OptIn(ExperimentalTime::class)

package com.jvcs.tracky.features.projecttracker.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectOrganizationRepository
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class OfflineFirstProjectRepositoryTest {

    private fun repo(
        local: FakeLocalProjectDataSource,
        remote: FakeRemoteProjectDataSource,
        queue: FakePendingSyncDataSource,
        scheduler: FakeSyncScheduler,
        time: FakeTimeProvider = FakeTimeProvider(),
    ) = OfflineFirstProjectRepository(
        localProjectDataSource = local,
        localProjectOrganizationDataSource = local,
        localServerTreeDataSource = local,
        remoteProjectDataSource = remote,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        timeProvider = time,
    )

    private fun organization(
        local: FakeLocalProjectDataSource,
        remote: FakeRemoteProjectDataSource,
        queue: FakePendingSyncDataSource,
        scheduler: FakeSyncScheduler,
        time: FakeTimeProvider = FakeTimeProvider(),
    ) = OfflineFirstProjectOrganizationRepository(
        projectRepository = repo(local, remote, queue, scheduler, time),
        localProjectDataSource = local,
        localProjectOrganizationDataSource = local,
        remoteProjectDataSource = remote,
        pendingSyncDataSource = queue,
        syncScheduler = scheduler,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        timeProvider = time,
    )

    private fun project(id: String) =
        Project(
            projectId = id,
            title = "title-$id",
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            isFinished = false,
            endDateTimeUtc = null,
        )

    @Test
    fun upsertProject_queuesCreate_whenRemoteOffline() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()

            val result = repo(local, remote, queue, scheduler).upsertProject(project("p1"))

            // User sees success because the local write succeeded.
            assertThat(result is Result.Success).isTrue()
            assertThat(local.projects["p1"]).isNotNull()

            val ops = queue.all()
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].entityType).isEqualTo(PendingSyncOperation.ENTITY_PROJECT)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_CREATE)
            assertThat(scheduler.scheduleCount > 0).isTrue()
        }

    @Test
    fun syncPendingOperations_pushesQueuedCreate_andClearsQueue() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            val repository = repo(local, remote, queue, scheduler)

            repository.upsertProject(project("p1")) // queued while offline
            remote.failWith = null // back online

            repository.syncPendingProjects()

            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.postedProjectIds.contains("p1")).isTrue()
        }

    /** Seeds three projects already carrying a contiguous order 0,1,2. */
    private fun FakeLocalProjectDataSource.seedOrderedProjects() {
        projects["p1"] = project("p1").copy(sortIndex = 0)
        projects["p2"] = project("p2").copy(sortIndex = 1)
        projects["p3"] = project("p3").copy(sortIndex = 2)
    }

    @Test
    fun reorderProjects_writesShiftedIndices_inASingleLocalWrite() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource() // online
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            // Move p3 to the middle -> new order p1, p3, p2.
            val result =
                organization(local, remote, queue, scheduler)
                    .reorderProjects(listOf("p1", "p3", "p2"))

            assertThat(result is Result.Success).isTrue()
            assertThat(local.projects["p1"]!!.sortIndex).isEqualTo(0L)
            assertThat(local.projects["p3"]!!.sortIndex).isEqualTo(1L)
            assertThat(local.projects["p2"]!!.sortIndex).isEqualTo(2L)
            // The whole gesture is one transaction, carrying only the two cards that actually moved.
            assertThat(local.sortIndexWrites.size).isEqualTo(1)
            assertThat(local.sortIndexWrites.single()).isEqualTo(mapOf("p3" to 1L, "p2" to 2L))
        }

    @Test
    fun reorderProjects_makesExactlyOneNetworkCall() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            organization(local, remote, queue, scheduler).reorderProjects(listOf("p1", "p3", "p2"))

            // One drag must not fan out into one PUT per shifted card.
            assertThat(remote.reorderCalls.size).isEqualTo(1)
            assertThat(remote.reorderCalls.single()).isEqualTo(mapOf("p3" to 1L, "p2" to 2L))
            assertThat(remote.updatedProjectIds.isEmpty()).isTrue()
        }

    @Test
    fun reorderProjects_whenNothingMoved_writesNothing() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            val result =
                organization(local, remote, queue, scheduler)
                    .reorderProjects(listOf("p1", "p2", "p3")) // already the stored order

            assertThat(result is Result.Success).isTrue()
            assertThat(local.sortIndexWrites.isEmpty()).isTrue()
            assertThat(remote.reorderCalls.isEmpty()).isTrue()
        }

    @Test
    fun reorderProjects_whenLocalWriteFails_doesNotHitNetwork_andLeavesOrderUntouched() =
        runBlocking {
            val local = FakeLocalProjectDataSource().apply { failSortIndexWrite = true }
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            val result =
                organization(local, remote, queue, scheduler)
                    .reorderProjects(listOf("p1", "p3", "p2"))

            assertThat(result is Result.Error).isTrue()
            assertThat(remote.reorderCalls.isEmpty()).isTrue()
            // Nothing landed: the old order is intact rather than half-applied.
            assertThat(local.projects["p1"]!!.sortIndex).isEqualTo(0L)
            assertThat(local.projects["p2"]!!.sortIndex).isEqualTo(1L)
            assertThat(local.projects["p3"]!!.sortIndex).isEqualTo(2L)
        }

    @Test
    fun reorderProjects_whenOffline_queuesOneOrderOp_andReportsSuccess() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            val result =
                organization(local, remote, queue, scheduler)
                    .reorderProjects(listOf("p1", "p3", "p2"))

            // User sees success because the local write succeeded.
            assertThat(result is Result.Success).isTrue()
            assertThat(local.projects["p3"]!!.sortIndex).isEqualTo(1L)

            val ops = queue.all()
            assertThat(ops.size).isEqualTo(1)
            assertThat(ops[0].entityType).isEqualTo(PendingSyncOperation.ENTITY_PROJECT_ORDER)
            assertThat(ops[0].operationType).isEqualTo(PendingSyncOperation.OP_UPDATE)
            assertThat(scheduler.scheduleCount > 0).isTrue()
        }

    @Test
    fun reorderProjects_twiceWhileOffline_stillQueuesOneOrderOp() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()
            val organization = organization(local, remote, queue, scheduler)

            organization.reorderProjects(listOf("p1", "p3", "p2"))
            organization.reorderProjects(listOf("p3", "p2", "p1"))

            // The order is a single piece of state — two drags collapse into one queued push.
            assertThat(queue.all().size).isEqualTo(1)
        }

    @Test
    fun syncPendingOperations_drainsOrderOp_withOneBatchCall() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()
            val repository = repo(local, remote, queue, scheduler)

            // Queued while offline.
            organization(local, remote, queue, scheduler).reorderProjects(listOf("p1", "p3", "p2"))
            remote.failWith = null // back online
            remote.reorderCalls.clear()

            repository.syncPendingProjects()

            assertThat(queue.all().isEmpty()).isTrue()
            // The queued row is rebuilt from current local state: the full order, in one call.
            assertThat(remote.reorderCalls.size).isEqualTo(1)
            assertThat(remote.reorderCalls.single()).isEqualTo(mapOf("p1" to 0L, "p3" to 1L, "p2" to 2L))
        }

    @Test
    fun reorderProjects_skipsIdsMissingLocally() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedOrderedProjects()

            // "ghost" was deleted on another device but is still in the mirror list the UI committed.
            val result =
                organization(local, remote, queue, scheduler)
                    .reorderProjects(listOf("ghost", "p1", "p2", "p3"))

            assertThat(result is Result.Success).isTrue()
            assertThat(local.sortIndexWrites.single().containsKey("ghost")).isFalse()
            assertThat(local.sortIndexWrites.single()).isEqualTo(mapOf("p1" to 1L, "p2" to 2L, "p3" to 3L))
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
    fun setProjectsPinned_putsPinnedProjectOnTopOfItsNewSection() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedTwoSections()

            // p4 sits at index 1 in Other, where p2 already sits at 1 in Pinned. Flipping the flag alone
            // would leave them sharing an index and let the creation date decide the order.
            val result = organization(local, remote, queue, scheduler).setProjectsPinned(listOf("p4"), isPinned = true)

            assertThat(result is Result.Success).isTrue()
            assertThat(local.projects["p4"]!!.isPinned).isTrue()
            assertThat(local.sectionOrder(isPinned = true)).isEqualTo(listOf("p4", "p1", "p2"))
            assertThat(listOf("p4", "p1", "p2").map { local.projects[it]!!.sortIndex }).isEqualTo(listOf(0L, 1L, 2L))
        }

    @Test
    fun setProjectsPinned_keepsRelativeOrder_whenPinningSeveralAtOnce() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedTwoSections()

            // Selection order is a Set's, so the repository must fall back on the stored order: p3 (0)
            // before p5 (2).
            organization(local, remote, queue, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

            assertThat(local.sectionOrder(isPinned = true)).isEqualTo(listOf("p3", "p5", "p1", "p2"))
        }

    @Test
    fun setProjectsPinned_unpinning_putsProjectOnTopOfOther() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedTwoSections()

            organization(local, remote, queue, scheduler).setProjectsPinned(listOf("p1"), isPinned = false)

            assertThat(local.projects["p1"]!!.isPinned).isFalse()
            assertThat(local.sectionOrder(isPinned = false)).isEqualTo(listOf("p1", "p3", "p4", "p5"))
            assertThat(local.sectionOrder(isPinned = true)).isEqualTo(listOf("p2"))
        }

    @Test
    fun setProjectsPinned_reindexesTheSectionInOneBatchCall() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedTwoSections()

            organization(local, remote, queue, scheduler).setProjectsPinned(listOf("p5", "p3"), isPinned = true)

            // One gesture, one /sort request — not one per shifted card. p3 already sat at 0 and stays
            // there, so it is not part of the write.
            assertThat(remote.reorderCalls.size).isEqualTo(1)
            assertThat(remote.reorderCalls.single()).isEqualTo(mapOf("p5" to 1L, "p1" to 2L, "p2" to 3L))
            assertThat(local.sortIndexWrites.size).isEqualTo(1)
        }

    @Test
    fun setProjectsPinned_skipsIdsMissingLocally() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            local.seedTwoSections()

            val result =
                organization(local, remote, queue, scheduler)
                    .setProjectsPinned(listOf("ghost"), isPinned = true)

            assertThat(result is Result.Success).isTrue()
            assertThat(local.sectionOrder(isPinned = true)).isEqualTo(listOf("p1", "p2"))
            assertThat(remote.reorderCalls.isEmpty()).isTrue()
        }

    @Test
    fun deleteProject_droppedLocally_whenStillPendingCreate_neverHitsServer() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource().apply { failWith = DataError.Remote.NO_INTERNET }
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            val repository = repo(local, remote, queue, scheduler)

            repository.upsertProject(project("p1")) // queued CREATE (never reached server)
            repository.deleteProject("p1")

            assertThat(local.projects["p1"]).isNull()
            assertThat(queue.all().isEmpty()).isTrue()
            assertThat(remote.deletedProjectIds.contains("p1")).isFalse()
        }

    @Test
    fun upsertProject_stampsUpdatedAt_fromTheInjectedClock() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(1_234_567))

            repo(local, remote, queue, scheduler, time).upsertProject(project("p1"))

            assertThat(remote.postedProjects.single().ownUpdatedAt).isEqualTo(time.now)
        }

    @Test
    fun reorderProjects_stampsLocalAndRemote_withTheSameInstant() =
        runBlocking {
            val local = FakeLocalProjectDataSource()
            val remote = FakeRemoteProjectDataSource()
            val queue = FakePendingSyncDataSource()
            val scheduler = FakeSyncScheduler()
            // Every read of the clock advances it, so a second read would produce a different stamp.
            val time = FakeTimeProvider(now = Instant.fromEpochMilliseconds(1_000), advanceOnReadMillis = 1)
            local.seedTwoSections()

            organization(local, remote, queue, scheduler, time).reorderProjects(listOf("p2", "p1"))

            assertThat(local.sortIndexWriteTimestamps.single()).isEqualTo(Instant.fromEpochMilliseconds(1_000))
            assertThat(remote.reorderTimestamps.single()).isEqualTo(Instant.fromEpochMilliseconds(1_000))
        }

    // --- Pull path --------------------------------------------------------------------------------

    private data class Quad(
        val local: FakeLocalProjectDataSource,
        val remote: FakeRemoteProjectDataSource,
        val queue: FakePendingSyncDataSource,
        val scheduler: FakeSyncScheduler,
    )

    private fun Quad.repository() = repo(local, remote, queue, scheduler)

    private fun serverTask(
        taskId: String,
        projectId: String,
        updatedAt: Long?,
        intervals: List<TaskInterval> = emptyList(),
    ) = ProjectTask(
        projectTaskId = taskId,
        title = "task-$taskId",
        description = null,
        durationMillis = 0,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        parentProjectId = projectId,
        isTimerRunning = false,
        intervals = intervals,
        ownUpdatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    private fun serverInterval(
        intervalId: String,
        taskId: String,
        end: Long?,
        projectId: String = "p1",
    ) = TaskInterval(
        intervalId = intervalId,
        parentTaskId = taskId,
        parentProjectId = projectId,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        endDateTimeUtc = end?.let { Instant.fromEpochMilliseconds(it) },
        durationMillis = end ?: 0L,
    )

    @Test
    fun fetchProjects_persistsNestedTasksAndIntervals() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        ownUpdatedAt = Instant.fromEpochMilliseconds(100),
                        projectTasks =
                            listOf(
                                serverTask(
                                    "t1",
                                    "p1",
                                    updatedAt = 100,
                                    intervals = listOf(serverInterval("i1", "t1", end = 60_000)),
                                ),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            // This is the fresh-install case: tracked time has to come back, not just the project row.
            assertThat(f.local.projects["p1"]).isNotNull()
            assertThat(f.local.tasks["t1"]).isNotNull()
            assertThat(
                f.local.intervals
                    .getValue("i1")
                    .durationMillis,
            ).isEqualTo(60_000L)
        }

    @Test
    fun fetchProjects_keepsALocalTaskEditThatIsNewerThanTheServer() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.tasks["t1"] = serverTask("t1", "p1", updatedAt = 500).copy(title = "edited offline")
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(projectTasks = listOf(serverTask("t1", "p1", updatedAt = 100))),
                )

            f.repository().fetchProjects()

            // Overwriting here would also feed the stale title back to the server on the next drain.
            assertThat(
                f.local.tasks
                    .getValue("t1")
                    .title,
            ).isEqualTo("edited offline")
        }

    @Test
    fun fetchProjects_closesAnIntervalStoppedOnAnotherDevice() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.intervals["i1"] = serverInterval("i1", "t1", end = null) // still ticking here
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask(
                                    "t1",
                                    "p1",
                                    updatedAt = 100,
                                    intervals = listOf(serverInterval("i1", "t1", end = 60_000)),
                                ),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            // Nothing queued for this row, so this device has no unsent change to defend: the stop
            // the user made on their other device is what lands.
            assertThat(
                f.local.intervals
                    .getValue("i1")
                    .endDateTimeUtc,
            ).isEqualTo(Instant.fromEpochMilliseconds(60_000))
        }

    @Test
    fun fetchProjects_doesNotCloseAnIntervalWhoseOwnStopIsStillQueued() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.intervals["i1"] = serverInterval("i1", "t1", end = null)
            f.local.pendingIntervalIds += "i1"
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask(
                                    "t1",
                                    "p1",
                                    updatedAt = 100,
                                    intervals = listOf(serverInterval("i1", "t1", end = 60_000)),
                                ),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            assertThat(
                f.local.intervals
                    .getValue("i1")
                    .endDateTimeUtc,
            ).isNull()
        }

    @Test
    fun fetchProjects_leavesLocalRowsTheServerDoesNotKnowAbout() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.tasks["local-only"] = serverTask("local-only", "p1", updatedAt = null)
            f.local.intervals["i-local"] = serverInterval("i-local", "local-only", end = 1_000)
            f.remote.projectsToReturn = listOf(project("p1").copy(projectTasks = emptyList()))

            f.repository().fetchProjects()

            // Created offline and still queued for upload — a pull must never delete these.
            assertThat(f.local.tasks.containsKey("local-only")).isTrue()
            assertThat(f.local.intervals.containsKey("i-local")).isTrue()
        }

    private fun serverSubTask(
        subTaskId: String,
        taskId: String,
        updatedAt: Long?,
        title: String = "sub-$subTaskId",
        projectId: String = "p1",
    ) = ProjectSubTask(
        projectSubTaskId = subTaskId,
        parentProjectTaskId = taskId,
        parentProjectId = projectId,
        title = title,
        durationMillis = 0,
        isTimerRunning = false,
        startDateTimeUtc = Instant.fromEpochMilliseconds(0),
        ownUpdatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    @Test
    fun fetchProjects_persistsNestedSubTasks() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask("t1", "p1", updatedAt = 100)
                                    .copy(subTasks = listOf(serverSubTask("s1", "t1", updatedAt = 100))),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            // The fresh-install case, one level deeper than tasks.
            assertThat(f.local.subTasks["s1"]).isNotNull()
            assertThat(
                f.local.subTasks
                    .getValue("s1")
                    .parentProjectTaskId,
            ).isEqualTo("t1")
        }

    @Test
    fun fetchProjects_keepsALocalSubTaskEditThatIsNewerThanTheServer() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.tasks["t1"] = serverTask("t1", "p1", updatedAt = 100)
            f.local.subTasks["s1"] = serverSubTask("s1", "t1", updatedAt = 500, title = "edited offline")
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask("t1", "p1", updatedAt = 100)
                                    .copy(subTasks = listOf(serverSubTask("s1", "t1", updatedAt = 100))),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            // Overwriting would also feed the stale title back to the server on the next drain.
            assertThat(
                f.local.subTasks
                    .getValue("s1")
                    .title,
            ).isEqualTo("edited offline")
        }

    @Test
    fun fetchProjects_adoptsASubTaskTheServerHasEditedMoreRecently() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.tasks["t1"] = serverTask("t1", "p1", updatedAt = 100)
            f.local.subTasks["s1"] = serverSubTask("s1", "t1", updatedAt = 100, title = "stale")
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask("t1", "p1", updatedAt = 100).copy(
                                    subTasks =
                                        listOf(
                                            serverSubTask("s1", "t1", updatedAt = 900, title = "renamed elsewhere"),
                                        ),
                                ),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            assertThat(
                f.local.subTasks
                    .getValue("s1")
                    .title,
            ).isEqualTo("renamed elsewhere")
        }

    @Test
    fun fetchProjects_skipsASubTaskWhoseParentTaskIsMissing_withoutLosingTheRestOfThePull() =
        runBlocking<Unit> {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            // A subtask nested under a task the merge did not write is a foreign key violation in Room,
            // and it throws inside the transaction carrying the whole pull. One bad row must not cost
            // the project and task rows around it.
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(
                        projectTasks =
                            listOf(
                                serverTask("t1", "p1", updatedAt = 100)
                                    .copy(subTasks = listOf(serverSubTask("orphan", "missing-task", updatedAt = 100))),
                            ),
                    ),
                )

            f.repository().fetchProjects()

            assertThat(f.local.subTasks["orphan"]).isNull()
            assertThat(f.local.projects["p1"]).isNotNull()
            assertThat(f.local.tasks["t1"]).isNotNull()
        }

    @Test
    fun fetchProjects_leavesALocalOnlySubTaskAlone() =
        runBlocking {
            val f =
                Quad(
                    FakeLocalProjectDataSource(),
                    FakeRemoteProjectDataSource(),
                    FakePendingSyncDataSource(),
                    FakeSyncScheduler(),
                )
            f.local.tasks["t1"] = serverTask("t1", "p1", updatedAt = 100)
            f.local.subTasks["local-only"] = serverSubTask("local-only", "t1", updatedAt = null)
            f.remote.projectsToReturn =
                listOf(
                    project("p1").copy(projectTasks = listOf(serverTask("t1", "p1", updatedAt = 100))),
                )

            f.repository().fetchProjects()

            // Created offline and still queued for upload — a pull must never delete these.
            assertThat(f.local.subTasks.containsKey("local-only")).isTrue()
        }
}
