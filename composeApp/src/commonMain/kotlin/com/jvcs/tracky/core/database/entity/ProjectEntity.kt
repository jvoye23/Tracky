package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = false)
    val projectId: String,
    val title: String,
    val description: String?,
    val color: Int?,
    val totalDuration: Long?,
    val startDateTimeEpochMs: Long,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeEpochMs: Long?,
    val isArchived: Boolean = false,
    val trashedAtEpochMs: Long? = null,
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)
