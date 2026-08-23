package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drains the pending-sync queue in dependency order: projects, tasks, task intervals, then
 * subtasks.
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
    private val subTaskRepository: SubTaskRepository
) : SyncRepository {

    override suspend fun syncPendingOperations() = withContext(Dispatchers.Default) {
        projectRepository.syncPendingProjects()
        taskRepository.syncPendingTasks()
        intervalRepository.syncPendingIntervals()
        subTaskRepository.syncPendingSubTasks()
    }
}
