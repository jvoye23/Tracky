package com.jvcs.tracky.core.database.relation

/**
 * Projection of just the columns a reorder needs. Reading these instead of whole projects keeps a
 * drag off the `ProjectWithTasksEntity` relation graph.
 */
data class ProjectSortIndexEntity(
    val projectId: String,
    val sortIndex: Long?,
)