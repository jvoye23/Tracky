package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_operations")
data class PendingSyncEntity(
    @PrimaryKey val operationId: String,
    val entityId: String,
    val entityType: String,    // "project" | "project_task" | "task_interval" | "project_order"
    val operationType: String, // "CREATE" | "UPDATE" | "DELETE"
    val createdAtEpochMs: Long,
    // For task DELETE ops we still need the parent project id after the task row is gone.
    // For interval ops this holds the parent *task* id instead — see ENTITY_INTERVAL.
    val parentEntityId: String? = null,
) {
    companion object {
        const val ENTITY_PROJECT = "project"
        const val ENTITY_TASK = "project_task"

        // Intervals are pushed through /api/projects/{projectId}/tasks/{taskId}/intervals, so a
        // queued op needs both ids. Only the task id is stored (in parentEntityId); the project id
        // is read off the parent task when the op drains. Deleting an interval never deletes its
        // task, so that lookup is available — and if the task really is gone, the server removed
        // its intervals by cascade and the op is correctly dropped.
        const val ENTITY_INTERVAL = "task_interval"

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
