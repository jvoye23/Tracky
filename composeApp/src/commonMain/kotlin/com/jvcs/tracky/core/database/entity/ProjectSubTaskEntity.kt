package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_sub_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectTaskEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["parentProjectTaskId"],
            onDelete = ForeignKey.CASCADE // Deleting a task will delete the subtasks under it
        ),
        // parentProjectId is denormalised rather than derived through the task, for the same reason
        // task_intervals carries it: it makes the project a direct parent of its subtasks, so a
        // project delete reaches them even if the task rows were ever to disappear on their own.
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE // Deleting a project will delete all associated subtasks
        )
    ],
    // Indexing the foreign keys is a best practice for performance
    indices = [Index(value = ["parentProjectTaskId"]), Index(value = ["parentProjectId"])]
)
data class ProjectSubTaskEntity(
    @PrimaryKey(autoGenerate = false)
    val projectSubTaskId: String,
    val parentProjectTaskId: String, // The Foreign Key link to the owning task
    val parentProjectId: String, // The Foreign Key link to the owning project
    val title: String,
    val description: String?,
    val durationMillis: Long?,
    val isTimerRunning: Boolean,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val isFinished: Boolean,
    val updatedAtEpochMs: Long? = null,
)
