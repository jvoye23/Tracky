package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_intervals")
data class SessionIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val intervalId: Long = 0,
    val parentSessionId: String,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val durationMillis: Long
)
