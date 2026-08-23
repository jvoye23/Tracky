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
     * The op's *immediate* parent — one link, never the whole ancestry.
     *
     * It only has to be stored for a DELETE, whose own row is gone by the time the op drains: a
     * task DELETE holds the project id, an interval DELETE the task id, a subtask DELETE the task
     * id, and a subtask-interval DELETE the subtask id. Everything above that is read off local
     * rows when the op drains, which never costs more than one hop because every table
     * denormalises `parentProjectId`. A CREATE or UPDATE re-reads its own row instead, so what is
     * stored here is unused.
     *
     * An ancestor row missing at drain time means it was deleted, and the server cascades a delete
     * to its children — so the op is dropped, never retried.
     */
    val parentEntityId: String? = null,
) {
    companion object {
        const val ENTITY_PROJECT = "project"
        const val ENTITY_TASK = "project_task"
        const val ENTITY_INTERVAL = "task_interval"
        const val ENTITY_SUBTASK = "project_sub_task"
        const val ENTITY_SUBTASK_INTERVAL = "sub_task_interval"

        // These strings are persisted in pending_sync_operations.entityType, so they cannot be
        // renamed once shipped: an upgraded device would still hold queue rows tagged with the old
        // value, and every drain filters by entityType, so those writes would sit there unrouted
        // and never reach the server.

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
