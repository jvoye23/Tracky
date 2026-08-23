package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity

data class SubTaskWithIntervals(
    @Embedded val subTask: ProjectSubTaskEntity,
    @Relation(
        parentColumn = "projectSubTaskId",
        entityColumn = "parentSubTaskId"
    )
    val intervals: List<SubTaskIntervalEntity>
)
