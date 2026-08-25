package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity

/**
 * A project's whole subtree in one read: every task with its own intervals and its subtasks.
 *
 * [ProjectWithTasksEntity] stays as it is for the list screens, which only need the bare tasks;
 * this is the shape the project detail screen needs to render subtasks.
 */
data class ProjectWithTaskTreeEntity(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        entity = ProjectTaskEntity::class,
        parentColumn = "projectId",
        entityColumn = "parentProjectId"
    )
    val projectTasks: List<TaskWithSubTasks>
)
