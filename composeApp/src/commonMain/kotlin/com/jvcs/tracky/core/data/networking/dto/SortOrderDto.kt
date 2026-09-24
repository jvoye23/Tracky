package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.Serializable

/** One moved project in a [com.jvcs.tracky.core.data.networking.ReorderProjectsRequest]. */
@Serializable
data class ProjectSortOrderDto(val projectId: String, val sortIndex: Long)

/** One moved task or subtask in a [com.jvcs.tracky.core.data.networking.ReorderTasksRequest]. */
@Serializable
data class TaskSortOrderDto(val id: String, val sortIndex: Long)
