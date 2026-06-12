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
    // For task DELETE ops we still need the parent project id after the task row is gone.
    val parentEntityId: String? = null,
) {
    companion object {
        const val ENTITY_PROJECT = "project"
        const val ENTITY_TASK = "project_task"

        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}
