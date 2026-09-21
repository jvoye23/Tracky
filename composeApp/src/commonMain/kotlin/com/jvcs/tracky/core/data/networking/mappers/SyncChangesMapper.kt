package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.dto.SyncChangesDto
import com.jvcs.tracky.core.data.networking.dto.TombstoneDto
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.sync.Tombstone
import kotlin.time.Instant

/**
 * Maps one page of the change feed.
 *
 * Rows below project level carry `parentProjectId` only in this flat feed — inside
 * `GET /api/projects` it is handed down from the enclosing project instead. A row that arrives
 * without one **is dropped**, because there is nothing to attach it to: the column is `NOT NULL`
 * and backs the cascading foreign key onto projects. Dropping the row rather than throwing keeps
 * one malformed entry from costing the whole delta, and the applier's parents-first skip already
 * behaves this way for a row whose parent is simply absent.
 *
 * A timestamp the server sends but the client cannot parse is treated as absent for the same
 * reason. The cursor is the only field whose absence is fatal, and it is non-null on the wire.
 */
fun SyncChangesDto.toSyncChanges(): SyncChanges = SyncChanges(
    cursor = cursor,
    serverNow = serverNowUtc?.let { runCatching { Instant.parse(it) }.getOrNull() },
    fullResyncRequired = fullResyncRequired,
    hasMore = hasMore,
    projects = projects.map { it.toProject() },
    tasks = tasks.mapNotNull { dto ->
        dto.parentProjectId?.let { dto.toProjectTask(it) }
    },
    taskIntervals = taskIntervals.mapNotNull { dto ->
        dto.parentProjectId?.let { dto.toTaskInterval(it, dto.startedByDeviceId) }
    },
    subTasks = subTasks.mapNotNull { dto ->
        dto.parentProjectId?.let { dto.toProjectSubTask(it) }
    },
    subTaskIntervals = subTaskIntervals.mapNotNull { dto ->
        dto.parentProjectId?.let {
            dto.toSubTaskInterval(it, startedParentTimer = false, dto.startedByDeviceId)
        }
    },
    tombstones = tombstones.map { it.toTombstone() }
)

fun TombstoneDto.toTombstone(): Tombstone = Tombstone(
    entityType = entityType,
    entityId = entityId
)
