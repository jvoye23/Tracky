package com.jvcs.tracky.features.project.data.export.dto

import kotlinx.serialization.Serializable

/**
 * The user-facing JSON export of one project tree (issue #46). Deliberately not a database dump: no
 * sync bookkeeping, device ids or parent ids — nesting already says what belongs to what.
 *
 * Instants are ISO-8601 UTC strings; [timeZone] is the zone the user exported from. Every duration is
 * given twice, as exact milliseconds for tools and as `HH:MM:SS` for people.
 */
@Serializable
data class ProjectExportDto(
    val formatVersion: Int,
    val app: String,
    val exportedAt: String,
    val timeZone: String,
    val project: ExportedProjectDto,
)

@Serializable
data class ExportedProjectDto(
    val id: String,
    val title: String,
    val description: String?,
    /** `#RRGGBB`, or null when the project has no color. */
    val color: String?,
    /** `active`, `finished`, `archived` or `trashed`. */
    val status: String,
    val start: String,
    val end: String?,
    val totalDurationMs: Long,
    val totalDuration: String,
    val tasks: List<ExportedTaskDto>,
)

@Serializable
data class ExportedTaskDto(
    val id: String,
    val title: String,
    val description: String?,
    val isFinished: Boolean,
    val start: String,
    val end: String?,
    val durationMs: Long,
    val duration: String,
    val intervals: List<ExportedIntervalDto>,
    val subtasks: List<ExportedSubTaskDto>,
)

@Serializable
data class ExportedSubTaskDto(
    val id: String,
    val title: String,
    val description: String?,
    val isFinished: Boolean,
    val start: String,
    val end: String?,
    val durationMs: Long,
    val duration: String,
    val intervals: List<ExportedIntervalDto>,
)

/** One stretch of tracked time. [end] is null while its timer is still running. */
@Serializable
data class ExportedIntervalDto(
    val id: String,
    val start: String,
    val end: String?,
    val durationMs: Long,
    val duration: String,
)
