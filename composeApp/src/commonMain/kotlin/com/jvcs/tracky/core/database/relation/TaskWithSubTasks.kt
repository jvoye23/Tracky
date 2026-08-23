package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity

/**
 * A task's whole subtree in one read: its own intervals plus its subtasks, each with theirs.
 *
 * [TaskWithIntervals] stays as it is for the callers that only need the task's own time; this is
 * the shape that maps to a fully hydrated domain `ProjectTask`.
 */
data class TaskWithSubTasks(
    @Embedded val task: ProjectTaskEntity,
    @Relation(
        parentColumn = "recordId",
        entityColumn = "parentTaskId"
    )
    val intervals: List<TaskIntervalEntity>,
    @Relation(
        entity = ProjectSubTaskEntity::class,
        parentColumn = "recordId",
        entityColumn = "parentProjectTaskId"
    )
    val subTasks: List<SubTaskWithIntervals>
)
