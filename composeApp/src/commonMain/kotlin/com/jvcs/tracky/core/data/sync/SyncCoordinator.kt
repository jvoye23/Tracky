package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Drains the pending-sync queue in dependency order: projects, tasks, task intervals, subtasks,
 * then subtask intervals.
 *
 * The order is not a preference — it is the API shape. A task is addressed as
 * `/api/projects/{projectId}/tasks`, an interval as `.../tasks/{taskId}/intervals` and a subtask as
 * `.../tasks/{taskId}/subtasks`, so a child
 * created while offline has no route until its parent has been accepted by the server. Each pass
 * additionally leaves an op queued when its parent still has a pending CREATE, so a project whose
 * push fails does not drag its tasks into a round of doomed requests.
 *
 * Everything past that ordering is last-write-wins on `ownUpdatedAt`, resolved per entity.
 */
class SyncCoordinator(
    private val projectRepository: ProjectRepository,
    private val taskRepository: ProjectTaskRepository,
    private val intervalRepository: IntervalRepository,
    private val subTaskRepository: SubTaskRepository,
    private val subTaskIntervalRepository: SubTaskIntervalRepository,
    /** CPU-bound ordering work; the repositories move their own I/O. */
    private val dispatcher: CoroutineDispatcher,
) : SyncRepository {

    override suspend fun syncPendingOperations(): EmptyResult<DataError> =
        withContext(dispatcher) {
            // Every level still runs when an earlier one could not read the queue, in the same
            // parent-first order as always: a later drain may get through, and anything it cannot
            // push yet stays queued behind its parent. The first failure is the one reported.
            listOf(
                projectRepository.syncPendingProjects(),
                taskRepository.syncPendingTasks(),
                intervalRepository.syncPendingIntervals(),
                subTaskRepository.syncPendingSubTasks(),
                subTaskIntervalRepository.syncPendingSubTaskIntervals(),
            ).firstOrNull { it is Result.Error } ?: Result.Success(Unit)
        }
}
