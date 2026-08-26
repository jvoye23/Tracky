package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "project_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE // Deleting a parent project will delete all associated tasks
        )
    ],
    // Indexing the foreign key is a best practice for performance
    indices = [Index(value = ["parentProjectId"])]
)
data class ProjectTaskEntity(
    @PrimaryKey(autoGenerate = false)
    val projectTaskId: String,
    val parentProjectId: String, // The Foreign Key link
    val title: String,
    val description: String?,
    val durationMillis: Long,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val isFinished: Boolean,
    val isTimerRunning: Boolean,
    val updatedAtEpochMs: Long? = null,
)