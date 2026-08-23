package com.jvcs.tracky.features.project.data.subtask

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.getOrDefault
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import kotlinx.coroutines.flow.Flow

/**
 * Subtask writes never leave the device — there is no subtask route to call — so this repository
 * has no remote data source and nothing to queue.
 *
 * What does leave the device is the *task* interval a subtask timer opens or closes. Those rows are
 * pushed through the interval and task repositories, which already own the queueing and conflict
 * rules for them, rather than being duplicated here.
 */
class OfflineFirstSubTaskRepository(
    private val localSubTaskDataSource: LocalSubTaskDataSource,
    private val localTaskDataSource: LocalTaskDataSource,
    private val intervalRepository: IntervalRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val timeProvider: TimeProvider
) : SubTaskRepository {

    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> =
        localSubTaskDataSource.getSubTasksForTask(taskId)

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError> =
        localSubTaskDataSource
            .upsertSubTask(subTask.copy(ownUpdatedAt = timeProvider.nowInstant))
            .asEmptyDataResult()

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError> =
        localSubTaskDataSource.deleteSubTask(subTaskId).asEmptyDataResult()

    override suspend fun startSubTask(subTaskId: String): EmptyResult<DataError> {
        val change = when (val started = localSubTaskDataSource.startSubTask(subTaskId)) {
            is Result.Success -> started.data
            is Result.Error -> return started.asEmptyDataResult()
        }
        // Null when the task timer was already running: that interval is already on its way to the
        // server, and the task's own row has not changed either.
        val opened = change.taskInterval ?: return Result.Success(Unit)
        val intervalResult = intervalRepository.createTaskInterval(opened)
        return pushParentTask(change, intervalResult)
    }

    override suspend fun stopSubTask(subTaskId: String): EmptyResult<DataError> {
        val change = when (val stopped = localSubTaskDataSource.stopSubTask(subTaskId)) {
            is Result.Success -> stopped.data ?: return Result.Success(Unit)
            is Result.Error -> return stopped.asEmptyDataResult()
        }
        val closed = change.taskInterval ?: return Result.Success(Unit)
        // The interval carries the measured span, the task the accumulated total and the cleared
        // flag. The interval goes first so a failure pushing the task cannot strand it — the same
        // order stopProjectTask uses.
        val intervalResult = intervalRepository.updateTaskInterval(closed)
        return pushParentTask(change, intervalResult)
    }

    /**
     * Pushes the parent task row, whose duration and timer flag the write just changed.
     *
     * Goes through [ProjectTaskRepository] rather than talking to the network directly so the task's
     * queueing and last-write-wins rules stay in one place. [intervalResult] is returned when the
     * task cannot be read back, so an interval failure is never masked by a later success.
     */
    private suspend fun pushParentTask(
        change: SubTaskTimerChange,
        intervalResult: EmptyResult<DataError>
    ): EmptyResult<DataError> {
        val taskId = change.taskInterval?.parentTaskId ?: return intervalResult
        val task = localTaskDataSource.getTaskById(taskId).getOrDefault(null)
            ?: return intervalResult
        return projectTaskRepository.upsertProjectTask(task)
    }
}
