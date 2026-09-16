package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Reads the running timer straight off the open interval rows, which is what makes it survive
 * process death: `TimeManager` holds its elapsed time over a monotonic mark that dies with the
 * process, while `startDateTimeEpochMs` is wall clock on disk.
 *
 * Local only. There is no push half here because nothing is written - observing a timer is a read,
 * and the start/stop paths that do write already sync through their own repositories.
 */
class OfflineFirstRunningTimerRepository(
    private val projectDao: ProjectDao
) : RunningTimerRepository {

    override fun observeRunningTimer(): Flow<RunningTimer?> =
        combine(
            projectDao.observeOpenSubTaskInterval(),
            projectDao.observeOpenTaskInterval(),
            ::Pair
        )
            // Both queries re-run on any write to their tables, so most emissions repeat the row
            // that was already there. Cut them before the joins below, not after.
            .distinctUntilChanged()
            .map { (openSubTaskInterval, openTaskInterval) ->
                // The subtask wins: timing one also opens the task interval enclosing it, so both
                // rows are open at once and only the inner one is the timer the user started.
                openSubTaskInterval?.let { toRunningTimer(it) }
                    ?: openTaskInterval?.let { toRunningTimer(it) }
            }
            .distinctUntilChanged()

    private suspend fun toRunningTimer(interval: SubTaskIntervalEntity): RunningTimer? {
        val subTask = projectDao.getSubTaskById(interval.parentSubTaskId) ?: return null
        val task = projectDao.getTaskById(subTask.parentProjectTaskId) ?: return null
        val project = projectDao.getProjectById(task.parentProjectId) ?: return null

        return RunningTimer(
            project = ProjectRef(id = project.projectId, title = project.title, colorArgb = project.color),
            useLightTextColor = project.useLightTextColor,
            task = TaskRef(id = task.projectTaskId, title = task.title),
            subTask = TaskRef(id = subTask.projectSubTaskId, title = subTask.title),
            startedAt = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs),
            // A subtask that has never been stopped has a null duration, not a zero one.
            bankedDuration = subTask.durationMillis?.milliseconds ?: Duration.ZERO
        )
    }

    private suspend fun toRunningTimer(interval: TaskIntervalEntity): RunningTimer? {
        val task = projectDao.getTaskById(interval.parentTaskId) ?: return null
        val project = projectDao.getProjectById(task.parentProjectId) ?: return null

        return RunningTimer(
            project = ProjectRef(id = project.projectId, title = project.title, colorArgb = project.color),
            useLightTextColor = project.useLightTextColor,
            task = TaskRef(id = task.projectTaskId, title = task.title),
            subTask = null,
            startedAt = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs),
            bankedDuration = task.durationMillis.milliseconds
        )
    }
}
