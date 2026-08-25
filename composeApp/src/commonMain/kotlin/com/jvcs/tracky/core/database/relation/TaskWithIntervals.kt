package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity

data class TaskWithIntervals(
    @Embedded val task: ProjectTaskEntity,
    @Relation(
        parentColumn = "projectTaskId",
        entityColumn = "parentTaskId"
    )
    val intervals: List<TaskIntervalEntity>
)
