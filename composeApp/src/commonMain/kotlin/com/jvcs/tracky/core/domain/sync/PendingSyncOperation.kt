package com.jvcs.tracky.core.domain.sync

import kotlin.time.Instant

/**
 * One queued write waiting to reach the server.
 *
 * The domain-layer twin of the Room row: repositories reason about these, never about the entity,
 * so the queue can be faked in tests without a database.
 */
data class PendingSyncOperation(
    val operationId: String,
    val entityId: String,
    val entityType: String,
    val operationType: String,
    val createdAt: Instant,
    /**
     * For a task DELETE this holds the parent *project* id, which is the only way to build the
     * route once the task row is gone. For an interval op it holds the parent *task* id instead —
     * the project id is read off that task when the op drains.
     */
    val parentEntityId: String? = null,
) {
    companion object {
        const val ENTITY_PROJECT = "project"
        const val ENTITY_TASK = "project_task"
        const val ENTITY_INTERVAL = "task_interval"

        // The manual project order is a single piece of state, not a per-project one: a queued
        // reorder is one row that gets rebuilt from current local state when it drains. The fixed
        // entityId is what makes the queue's dedup collapse repeat reorders — every other entityId
        // in the table is a UUID, so it cannot collide.
        const val ENTITY_PROJECT_ORDER = "project_order"
        const val PROJECT_ORDER_ENTITY_ID = "project_order"

        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}
