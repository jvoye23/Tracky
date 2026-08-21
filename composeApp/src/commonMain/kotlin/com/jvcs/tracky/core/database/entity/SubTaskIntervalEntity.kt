package com.jvcs.tracky.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sub_task_intervals",
    foreignKeys = [
        ForeignKey(
            entity = ProjectSubTaskEntity::class,
            parentColumns = ["projectSubTaskId"],
            childColumns = ["parentSubTaskId"],
            onDelete = ForeignKey.CASCADE // Deleting a subtask will delete the time it tracked
        ),
        // Every subtask interval sits inside exactly one task interval: timing a subtask also runs
        // its parent task's timer. Closing the enclosing interval therefore closes this one too, and
        // deleting it takes this one with it.
        ForeignKey(
            entity = TaskIntervalEntity::class,
            parentColumns = ["intervalId"],
            childColumns = ["parentTaskIntervalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE // Deleting a project will delete all associated intervals
        )
    ],
    // Indexing the foreign keys is a best practice for performance
    indices = [
        Index(value = ["parentSubTaskId"]),
        Index(value = ["parentTaskIntervalId"]),
        Index(value = ["parentProjectId"])
    ]
)
data class SubTaskIntervalEntity(
    @PrimaryKey(autoGenerate = false)
    val subTaskIntervalId: String,
    val parentSubTaskId: String, // The Foreign Key link to the owning subtask
    val parentTaskIntervalId: String, // The Foreign Key link to the enclosing task interval
    val parentProjectId: String, // The Foreign Key link to the owning project
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val durationMillis: Long,
    // True when starting this subtask is what opened the enclosing task interval, so stopping it
    // should stop the parent task too. False when the task timer was already running on its own.
    //
    // The default is declared here as well as in MIGRATION_14_15: `ADD COLUMN NOT NULL` cannot omit
    // one, and without it on the entity the two schemas would only agree because Room happens to
    // skip comparing defaults the entity does not declare.
    @ColumnInfo(defaultValue = "0")
    val startedParentTimer: Boolean = false
)
