package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_operations")
data class PendingSyncEntity(
    @PrimaryKey val operationId: String,
    val entityId: String,
    val entityType: String,    // "project" | "project_task"
    val operationType: String, // "CREATE" | "UPDATE" | "DELETE"
    val createdAtEpochMs: Long,
)
