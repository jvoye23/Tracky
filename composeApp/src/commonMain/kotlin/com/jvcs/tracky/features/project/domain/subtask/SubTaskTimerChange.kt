package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval

/**
 * Everything one subtask timer write touched.
 *
 * A subtask interval is local-only, but the task interval enclosing it is not: starting a subtask
 * can open one and stopping it can close one, and those rows sync. Their ids are generated inside
 * the data source, so handing them back is the only way the repository can know what to push —
 * without this, time tracked through subtasks would never reach the server.
 *
 * @param taskInterval the task interval this write opened or closed, or null when the task's timer
 *   was already running and is left alone.
 */
data class SubTaskTimerChange(
    val subTaskInterval: SubTaskInterval,
    val taskInterval: TaskInterval? = null
)
