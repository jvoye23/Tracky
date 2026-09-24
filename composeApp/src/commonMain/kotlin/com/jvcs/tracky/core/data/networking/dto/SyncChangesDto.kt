package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.Serializable

/**
 * The response of `GET /api/sync/changes?since=<cursor>`.
 *
 * See `Requirements/backend-delta-sync-api.md`. Two things differ from `GET /api/projects` and
 * both are deliberate:
 *
 * The collections are **flat, not nested**. A delta routinely carries a task whose project did not
 * change, and nesting would force the server to send unchanged parents. Each row already names its
 * parent, and the client applies them parents-first.
 *
 * It carries **tombstones**. Absence means nothing in a delta — a row the server omits may simply
 * be unchanged, or may have been created on this device and not pushed yet — so a deletion has to
 * be stated. This is what finally lets a project deleted on one device disappear on another.
 *
 * Every collection is defaulted so a deployment that predates part of this still decodes, and so a
 * server that omits an empty array rather than sending `[]` does not fail the whole pull.
 */
@Serializable
data class SyncChangesDto(
    /** The highest sequence number included here — not the server's current maximum. */
    val cursor: Long,
    /** The server's own clock, the only trustworthy input to the timer's skew correction. */
    val serverNowUtc: String? = null,
    /**
     * True when `since` predates the tombstone retention window. The server cannot prove what was
     * deleted in the gap, so the client falls back to a full tree pull instead of diverging.
     */
    val fullResyncRequired: Boolean = false,
    /** True when more rows remain above [cursor]; the client immediately requests again. */
    val hasMore: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val tasks: List<ProjectTaskDto> = emptyList(),
    val taskIntervals: List<TaskIntervalDto> = emptyList(),
    val subTasks: List<ProjectSubTaskDto> = emptyList(),
    val subTaskIntervals: List<SubTaskIntervalDto> = emptyList(),
    val tombstones: List<TombstoneDto> = emptyList(),
)

/**
 * One deleted row.
 *
 * [entityType] is the *server's* vocabulary — `project | task | task_interval | sub_task |
 * sub_task_interval`, per `backend-delta-sync-api.md` §3 — and the constants for it live on
 * `Tombstone`.
 *
 * It is emphatically **not** the outbox's `PendingSyncOperation` vocabulary, which this was once
 * documented as being on the grounds that "the applier compares tombstones against queued
 * operations by id, and matching strings keep that comparison honest". That reasoning refutes
 * itself: the comparison is by id, so the type strings never had to match, and they did not.
 */
@Serializable
data class TombstoneDto(
    val entityType: String,
    val entityId: String,
    val deletedAtUtc: String? = null,
)
