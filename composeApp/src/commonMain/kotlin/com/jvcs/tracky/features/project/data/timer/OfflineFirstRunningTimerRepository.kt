package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.SubTaskIntervalDao
import com.jvcs.tracky.core.database.dao.TaskDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.timer.isForeignTimer
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.RunningTimer
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Reads the running timer straight off the open interval rows, which is what makes it survive
 * process death: `TimeManager` holds its elapsed time over a monotonic mark that dies with the
 * process, while `startDateTimeEpochMs` is wall clock on disk.
 *
 * Local only. There is no push half here because nothing is written - observing a timer is a read,
 * and the start/stop paths that do write already sync through their own repositories.
 *
 * The banked total is summed from closed intervals rather than read off the task or subtask row.
 * Those rows carry a running total maintained by whichever device stopped the timer, and a device
 * that has just adopted a foreign timer may not have pulled it yet — see
 * [ProjectDao.getBankedTaskDuration].
 */
class OfflineFirstRunningTimerRepository(
    private val projectDao: ProjectDao,
    private val taskDao: TaskDao,
    private val subTaskIntervalDao: SubTaskIntervalDao,
    private val taskIntervalDao: TaskIntervalDao,
    private val deviceIdProvider: DeviceIdProvider,
) : RunningTimerRepository {

    override fun observeRunningTimer(): Flow<RunningTimer?> =
        combine(
            subTaskIntervalDao.observeOpenSubTaskInterval(),
            taskIntervalDao.observeOpenTaskInterval(),
            ::Pair,
        )
            // Both queries re-run on any write to their tables, so most emissions repeat the row
            // that was already there. Cut them before the joins below, not after.
            .distinctUntilChanged()
            .map { (openSubTaskInterval, openTaskInterval) ->
                // The subtask wins: timing one also opens the task interval enclosing it, so both
                // rows are open at once and only the inner one is the timer the user started.
                openSubTaskInterval?.let { toRunningTimer(it) }
                    ?: openTaskInterval?.let { toRunningTimer(it) }
            }.distinctUntilChanged()

    private suspend fun toRunningTimer(interval: SubTaskIntervalEntity): RunningTimer? {
        val subTask = projectDao.getSubTaskById(interval.parentSubTaskId) ?: return null
        val task = taskDao.getTaskById(subTask.parentProjectTaskId) ?: return null
        val project = projectDao.getProjectById(task.parentProjectId) ?: return null

        return RunningTimer(
            project = ProjectRef(id = project.projectId, title = project.title, colorArgb = project.color),
            useLightTextColor = project.useLightTextColor,
            task = TaskRef(id = task.projectTaskId, title = task.title),
            subTask = TaskRef(id = subTask.projectSubTaskId, title = subTask.title),
            startedAt = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs),
            bankedDuration = projectDao.getBankedSubTaskDuration(subTask.projectSubTaskId).milliseconds,
            isForeign = isForeignTimer(interval.startedByDeviceId, deviceIdProvider.deviceId()),
        )
    }

    private suspend fun toRunningTimer(interval: TaskIntervalEntity): RunningTimer? {
        val task = taskDao.getTaskById(interval.parentTaskId) ?: return null
        val project = projectDao.getProjectById(task.parentProjectId) ?: return null

        return RunningTimer(
            project = ProjectRef(id = project.projectId, title = project.title, colorArgb = project.color),
            useLightTextColor = project.useLightTextColor,
            task = TaskRef(id = task.projectTaskId, title = task.title),
            subTask = null,
            startedAt = Instant.fromEpochMilliseconds(interval.startDateTimeEpochMs),
            bankedDuration = taskDao.getBankedTaskDuration(task.projectTaskId).milliseconds,
            isForeign = isForeignTimer(interval.startedByDeviceId, deviceIdProvider.deviceId()),
        )
    }
}
