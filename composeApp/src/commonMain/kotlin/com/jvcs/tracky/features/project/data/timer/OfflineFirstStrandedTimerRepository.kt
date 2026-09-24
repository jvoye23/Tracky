package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.StrandedIntervalDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.data.mappers.toProjectSubTask
import com.jvcs.tracky.features.project.data.mappers.toProjectTask
import com.jvcs.tracky.features.project.data.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toTaskInterval
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.domain.timer.StrandedTimer
import com.jvcs.tracky.features.project.domain.timer.StrandedTimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Resolves a parked timer, then pushes the result the same way the ordinary stop path does.
 *
 * Local write first, then the push, in the order `OfflineFirstSubTaskRepository.pushTimerChange`
 * uses: the interval rows before the task and subtask rows they changed, so a failure pushing a
 * parent can never strand the child it belongs to.
 */
class OfflineFirstStrandedTimerRepository(
    private val projectDao: ProjectDao,
    private val taskIntervalDao: TaskIntervalDao,
    private val strandedIntervalDao: StrandedIntervalDao,
    private val intervalRepository: IntervalRepository,
    private val subTaskIntervalRepository: SubTaskIntervalRepository,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
) : StrandedTimerRepository {

    override fun observeStrandedTimers(): Flow<List<StrandedTimer>> =
        strandedIntervalDao.observeStrandedIntervals().map { parked -> parked.toTimers() }

    /**
     * Folds the parked rows into review items.
     *
     * A parked subtask interval swallows its parent task interval when that is parked too: the two
     * are one stretch of wall clock and have to be resolved together.
     */
    private suspend fun List<StrandedIntervalEntity>.toTimers(): List<StrandedTimer> {
        val subTaskParked = filter { it.isSubTaskInterval }
        val taskParked = filterNot { it.isSubTaskInterval }.associateBy { it.intervalId }

        val claimedTaskIntervalIds = mutableSetOf<String>()
        val fromSubTasks =
            subTaskParked.mapNotNull { parked ->
                val interval = projectDao.getSubTaskIntervalById(parked.intervalId) ?: return@mapNotNull null
                val subTask = projectDao.getSubTaskById(interval.parentSubTaskId) ?: return@mapNotNull null
                val task = projectDao.getTaskById(subTask.parentProjectTaskId) ?: return@mapNotNull null
                val project = projectDao.getProjectById(task.parentProjectId) ?: return@mapNotNull null

                // Only claim the parent if it is parked too. A subtask nested in a task interval the
                // user started by hand and legitimately closed is an orphan, resolved on its own.
                val parentIsParked = taskParked.containsKey(interval.parentTaskIntervalId)
                if (parentIsParked) claimedTaskIntervalIds += interval.parentTaskIntervalId

                // The outer span is what "running since" means, and the parent always opens first, so
                // a paired item is dated from the task interval. An edited duration measured from the
                // subtask's start instead would quietly stretch the parent to match.
                val parentInterval =
                    interval.parentTaskIntervalId
                        .takeIf { parentIsParked }
                        ?.let { taskIntervalDao.getIntervalById(it) }

                StrandedTimer(
                    taskIntervalId = parentInterval?.intervalId,
                    subTaskIntervalId = interval.subTaskIntervalId,
                    taskId = task.projectTaskId,
                    taskTitle = task.title,
                    projectTitle = project.title,
                    subTaskTitle = subTask.title,
                    startedAt =
                        Instant.fromEpochMilliseconds(
                            parentInterval?.startDateTimeEpochMs ?: interval.startDateTimeEpochMs,
                        ),
                    proposedEndAt = Instant.fromEpochMilliseconds(parked.detectedAtEpochMs),
                )
            }

        val fromTasks =
            taskParked.values
                .filterNot { it.intervalId in claimedTaskIntervalIds }
                .mapNotNull { parked ->
                    val interval = taskIntervalDao.getIntervalById(parked.intervalId) ?: return@mapNotNull null
                    val task = projectDao.getTaskById(interval.parentTaskId) ?: return@mapNotNull null
                    val project = projectDao.getProjectById(task.parentProjectId) ?: return@mapNotNull null
                    val hasSubTasks = projectDao.countSubTasks(task.projectTaskId) > 0

                    StrandedTimer(
                        taskIntervalId = interval.intervalId,
                        subTaskIntervalId = null,
                        taskId = task.projectTaskId,
                        taskTitle = task.title,
                        projectTitle = project.title,
                        subTaskTitle = null,
                        startedAt = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs),
                        proposedEndAt = Instant.fromEpochMilliseconds(parked.detectedAtEpochMs),
                        keepingWouldNotBeCounted = hasSubTasks,
                    )
                }

        return (fromSubTasks + fromTasks).sortedBy { it.startedAt }
    }

    override suspend fun keep(timer: StrandedTimer): EmptyResult<DataError> = close(timer, timer.proposedEndAt)

    override suspend fun keepWithDuration(timer: StrandedTimer, duration: Duration): EmptyResult<DataError> =
        close(timer, timer.startedAt + duration.coerceAtLeast(Duration.ZERO))

    private suspend fun close(timer: StrandedTimer, endAt: Instant): EmptyResult<DataError> {
        // Child first, at the same instant, exactly as RoomLocalTaskDataSource.stopTask does: a
        // subtask cannot outlive the task interval it sits in.
        val closedSubTaskInterval =
            timer.subTaskIntervalId?.let { id ->
                val interval = projectDao.getSubTaskIntervalById(id) ?: return@let null
                // An edited duration shorter than the subtask's own start would write a negative span.
                // Nothing defensible is left to keep, so the row goes instead.
                if (endAt.toEpochMilliseconds() < interval.startDateTimeEpochMs) {
                    strandedIntervalDao.deleteStrandedInterval(id)
                    subTaskIntervalRepository.deleteSubTaskInterval(id)
                    null
                } else {
                    projectDao.closeSubTaskInterval(interval, endAt).also {
                        strandedIntervalDao.deleteStrandedInterval(id)
                    }
                }
            }

        val closedTaskInterval =
            timer.taskIntervalId?.let { id ->
                val interval = taskIntervalDao.getIntervalById(id) ?: return@let null
                taskIntervalDao.closeTaskInterval(interval, endAt, projectDao).also {
                    strandedIntervalDao.deleteStrandedInterval(id)
                }
            }

        val subTaskIntervalResult =
            closedSubTaskInterval
                ?.let { subTaskIntervalRepository.updateSubTaskInterval(it.toSubTaskInterval()) }
        val taskIntervalResult =
            closedTaskInterval
                ?.let { intervalRepository.updateTaskInterval(it.toTaskInterval()) }

        // Re-read: closing is what banked the durations and cleared the flags, so the new values
        // exist nowhere else.
        val subTaskResult =
            closedSubTaskInterval?.let { closed ->
                projectDao
                    .getSubTaskById(closed.parentSubTaskId)
                    ?.let { subTaskRepository.upsertSubTask(it.toProjectSubTask()) }
            }
        val taskResult =
            projectDao
                .getTaskById(timer.taskId)
                ?.let { projectTaskRepository.upsertProjectTask(it.toProjectTask()) }

        return firstError(subTaskIntervalResult, taskIntervalResult, subTaskResult, taskResult)
            ?: Result.Success(Unit)
    }

    override suspend fun discard(timer: StrandedTimer): EmptyResult<DataError> {
        // Remote as well as local: the row is already on the server, and upsertServerTree inserts
        // an incoming interval unconditionally when the local one is gone, so a local-only delete
        // is resurrected by the next pull.
        val subTaskIntervalResult =
            timer.subTaskIntervalId?.let { id ->
                strandedIntervalDao.deleteStrandedInterval(id)
                subTaskIntervalRepository.deleteSubTaskInterval(id)
            }
        val taskIntervalResult =
            timer.taskIntervalId?.let { id ->
                strandedIntervalDao.deleteStrandedInterval(id)
                intervalRepository.deleteTaskInterval(id)
            }

        // The task kept its timer flag cleared by the reconciler, but push the row so the server
        // stops believing a timer is running on this task.
        val taskResult =
            projectDao
                .getTaskById(timer.taskId)
                ?.let { projectTaskRepository.upsertProjectTask(it.toProjectTask()) }

        return firstError(subTaskIntervalResult, taskIntervalResult, taskResult)
            ?: Result.Success(Unit)
    }

    private fun firstError(vararg results: EmptyResult<DataError>?): EmptyResult<DataError>? =
        results.filterIsInstance<Result.Error<DataError>>().firstOrNull()
}
