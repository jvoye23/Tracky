package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "project_records",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE // Deleting a parent project will delete all associated records
        )
    ],
    // Indexing the foreign key is a best practice for performance
    indices = [Index(value = ["parentProjectId"])]
)
data class ProjectTaskEntity(
    @PrimaryKey
    val recordId: String,
    val parentProjectId: String, // The Foreign Key link
    val description: String,
    val durationMillis: Long,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val isFinished: Boolean,
    val isTimerRunning: Boolean
)