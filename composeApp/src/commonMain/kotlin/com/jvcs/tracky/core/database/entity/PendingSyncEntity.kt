package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Storage form of a queued write. The `entityType` / `operationType` vocabulary lives on the domain
 * model [com.jvcs.tracky.core.domain.sync.PendingSyncOperation] so there is only one copy of it.
 */
@Entity(tableName = "pending_sync_operations")
data class PendingSyncEntity(
    @PrimaryKey val operationId: String,
    val entityId: String,
    val entityType: String,    // "project" | "project_task" | "task_interval" | "project_order"
    val operationType: String, // "CREATE" | "UPDATE" | "DELETE"
    val createdAtEpochMs: Long,
    // For task DELETE ops we still need the parent project id after the task row is gone.
    // For interval ops this holds the parent *task* id instead.
    val parentEntityId: String? = null,
)
