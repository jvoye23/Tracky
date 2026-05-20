package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_intervals")
data class TaskIntervalEntity(
    @PrimaryKey(autoGenerate = false)
    val intervalId: String,
    val parentTaskId: String,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val durationMillis: Long
)
