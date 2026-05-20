package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity

data class ProjectWithTasksEntity(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        parentColumn = "projectId",
        entityColumn = "parentProjectId"
    )
    val projectTasks: List<ProjectTaskEntity>
)