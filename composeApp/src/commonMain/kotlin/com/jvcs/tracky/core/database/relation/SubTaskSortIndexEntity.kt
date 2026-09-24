package com.jvcs.tracky.core.database.relation

/** Projection of just the columns a subtask reorder needs. See [TaskSortIndexEntity]. */
data class SubTaskSortIndexEntity(val projectSubTaskId: String, val sortIndex: Long?)
