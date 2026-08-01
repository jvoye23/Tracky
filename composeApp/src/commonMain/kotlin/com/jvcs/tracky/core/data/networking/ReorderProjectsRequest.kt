package com.jvcs.tracky.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Body of PUT /api/projects/sort. Carries only the projects whose index actually moved — every
 * project left out keeps the sortIndex the server already has.
 */
@Serializable
data class ReorderProjectsRequest(
    val updatedAtUtc: String,
    val items: List<ProjectSortOrderDto>,
)

@Serializable
data class ProjectSortOrderDto(
    val projectId: String,
    val sortIndex: Long,
)
