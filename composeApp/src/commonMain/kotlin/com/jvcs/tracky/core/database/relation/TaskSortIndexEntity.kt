package com.jvcs.tracky.core.database.relation

/**
 * Projection of just the columns a task reorder needs. Reading these instead of whole tasks keeps a
 * drag off the `TaskWithSubTasks` relation graph. The twin of [ProjectSortIndexEntity].
 */
data class TaskSortIndexEntity(
    val projectTaskId: String,
    val sortIndex: Long?,
)
