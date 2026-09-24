package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body of PUT /api/projects/{projectId}/tasks/sort and of the subtask route one level down.
 *
 * The shape is [ReorderProjectsRequest]'s, with the parent in the path rather than in the body, so
 * one type serves both levels. Like the project one it carries only the rows whose index actually
 * moved — anything left out keeps the sortIndex the server already has.
 */
@Serializable
data class ReorderTasksRequest(val updatedAtUtc: String, val items: List<TaskSortOrderDto>)

@Serializable
data class TaskSortOrderDto(val id: String, val sortIndex: Long)
