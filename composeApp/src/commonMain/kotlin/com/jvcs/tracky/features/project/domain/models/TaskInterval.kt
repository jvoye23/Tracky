package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

data class TaskInterval(
    val intervalId: String,
    val parentTaskId: String,
    // Carried alongside parentTaskId so the interval knows its whole ancestry: it backs the
    // cascading foreign key onto projects, and it spares the sync layer a lookup to build the
    // /api/projects/{projectId}/tasks/{taskId}/intervals route.
    val parentProjectId: String,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val durationMillis: Long
) : Timestamped {
    // Intervals do sync remotely, but they carry no timestamp of their own. They are written by
    // one device's timer rather than edited by hand, so a duplicate CREATE is retried as an UPDATE
    // instead of being resolved by last-write-wins — which means there is nothing to compare and
    // nothing to stamp. Staying null also keeps them out of the lastUpdatedAt roll-up.
    override val ownUpdatedAt: Instant? get() = null
}