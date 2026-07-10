package com.jvcs.tracky.core.domain.sync

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Platform-agnostic trigger for the deferred "empty the trash" job. Trashed projects (those with a
 * non-null trashedAt) are permanently purged, locally and on the server, once they are older than
 * [TrashRetention.RETENTION]. Android backs this with a periodic WorkManager worker, iOS with a
 * BGProcessingTask, JVM with a no-op. Scheduling is idempotent and kicked off once on app start.
 */
interface TrashCleanupScheduler {
    suspend fun scheduleCleanup()
    suspend fun cancelCleanup()
}

object TrashRetention {
    /** How long a soft-deleted project stays in the trash before it is permanently removed. */
    val RETENTION: Duration = 30.days

    /** Projects trashed before this instant are eligible for permanent deletion. */
    fun cutoff(now: Instant = Clock.System.now()): Instant = now - RETENTION
}
