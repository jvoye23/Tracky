package com.jvcs.tracky.core.database.relation

/**
 * Projection of when each project last banked time, for the list screens. Their relation graph
 * stops at tasks, so this is read beside it rather than hydrating every interval for one maximum.
 */
data class ProjectLastTrackedEntity(val projectId: String, val lastTrackedEndEpochMs: Long)
