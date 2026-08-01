package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_operations")
data class PendingSyncEntity(
    @PrimaryKey val operationId: String,
    val entityId: String,
    val entityType: String,    // "project" | "project_task" | "project_order"
    val operationType: String, // "CREATE" | "UPDATE" | "DELETE"
    val createdAtEpochMs: Long,
    // For task DELETE ops we still need the parent project id after the task row is gone.
    val parentEntityId: String? = null,
) {
    companion object {
        const val ENTITY_PROJECT = "project"
        const val ENTITY_TASK = "project_task"

        // The manual project order is a single piece of state, not a per-project one: a queued
        // reorder is one row that gets rebuilt from current local state when it drains. The fixed
        // entityId is what makes enqueueDeduped collapse repeat reorders — every other entityId in
        // this table is a UUID, so it cannot collide.
        const val ENTITY_PROJECT_ORDER = "project_order"
        const val PROJECT_ORDER_ENTITY_ID = "project_order"

        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}
