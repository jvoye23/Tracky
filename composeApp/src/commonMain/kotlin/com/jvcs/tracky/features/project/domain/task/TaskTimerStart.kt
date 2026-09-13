package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.features.project.domain.models.TaskInterval

/**
 * What starting a task's timer actually did.
 *
 * Starting reuses an interval that is already open rather than stacking a second one on top, so the
 * caller cannot assume it has a new row to push. Mirrors
 * [com.jvcs.tracky.features.project.domain.subtask.SubTaskTimerChange]'s nullable `taskInterval`,
 * which carries the same "nothing new to push" signal one level down.
 *
 * @param interval the interval the timer is now running against, new or reused.
 * @param openedInterval the same row when this call created it, or null when an already-open one
 *   was reused — that row is already on the server, or already queued for it.
 */
data class TaskTimerStart(
    val interval: TaskInterval,
    val openedInterval: TaskInterval?
)
