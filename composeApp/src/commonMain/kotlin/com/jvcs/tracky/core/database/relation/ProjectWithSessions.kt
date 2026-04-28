package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity

data class ProjectWithSessions(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        parentColumn = "projectId",
        entityColumn = "parentProjectId"
    )
    val projectSessions: List<ProjectSessionEntity>
)