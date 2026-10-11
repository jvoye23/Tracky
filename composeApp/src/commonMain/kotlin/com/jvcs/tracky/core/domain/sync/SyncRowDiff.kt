package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.features.project.domain.models.Project

/**
 * One row of a project tree, flattened, with the content that decides whether it changed.
 *
 * [content] is the row with its children stripped, so a task does not count as changed because one
 * of its intervals did - the interval is its own row.
 */
data class SyncRow(
    val entityId: String,
    val entityType: String,
    val parentEntityId: String?,
    val content: Any,
)

/** A queue operation the comparison asks for. */
data class SyncRowChange(val row: SyncRow, val operationType: String)

/** Every row of every tree, by id, carrying the parent link a queued DELETE needs. */
fun List<Project>.flattenForSync(): Map<String, SyncRow> =
    buildList {
        this@flattenForSync.forEach { project ->
            add(
                SyncRow(
                    entityId = project.projectId,
                    entityType = PendingSyncOperation.ENTITY_PROJECT,
                    parentEntityId = null,
                    // lastTrackedEndUtc is derived, never stored or synced.
                    content = project.copy(projectTasks = null, lastTrackedEndUtc = null),
                ),
            )
            project.projectTasks.orEmpty().forEach { task ->
                add(
                    SyncRow(
                        entityId = task.projectTaskId,
                        entityType = PendingSyncOperation.ENTITY_TASK,
                        parentEntityId = project.projectId,
                        content = task.copy(intervals = emptyList(), subTasks = null),
                    ),
                )
                task.intervals.forEach { interval ->
                    add(
                        SyncRow(
                            entityId = interval.intervalId,
                            entityType = PendingSyncOperation.ENTITY_INTERVAL,
                            parentEntityId = task.projectTaskId,
                            content = interval,
                        ),
                    )
                }
                task.subTasks.orEmpty().forEach { subTask ->
                    add(
                        SyncRow(
                            entityId = subTask.projectSubTaskId,
                            entityType = PendingSyncOperation.ENTITY_SUBTASK,
                            parentEntityId = task.projectTaskId,
                            content = subTask.copy(subTaskIntervals = emptyList()),
                        ),
                    )
                    subTask.subTaskIntervals.forEach { interval ->
                        add(
                            SyncRow(
                                entityId = interval.subTaskIntervalId,
                                entityType = PendingSyncOperation.ENTITY_SUBTASK_INTERVAL,
                                parentEntityId = subTask.projectSubTaskId,
                                content = interval,
                            ),
                        )
                    }
                }
            }
        }
    }.associateBy { it.entityId }

/**
 * What has to be queued to bring the server from [before] to [after]: a CREATE for a new row, an
 * UPDATE for a changed one and a DELETE for one that is gone.
 *
 * A DELETE is queued for every vanished row, children included. The drain drops one whose ancestor
 * is gone too, since the server cascades the parent's delete, so the extra rows cost nothing.
 */
fun diffForSync(before: Map<String, SyncRow>, after: Map<String, SyncRow>): List<SyncRowChange> =
    buildList {
        after.values.forEach { row ->
            when (before[row.entityId]?.content) {
                null -> add(SyncRowChange(row, PendingSyncOperation.OP_CREATE))
                row.content -> Unit
                else -> add(SyncRowChange(row, PendingSyncOperation.OP_UPDATE))
            }
        }
        before.values.forEach { row ->
            if (row.entityId !in after) add(SyncRowChange(row, PendingSyncOperation.OP_DELETE))
        }
    }
