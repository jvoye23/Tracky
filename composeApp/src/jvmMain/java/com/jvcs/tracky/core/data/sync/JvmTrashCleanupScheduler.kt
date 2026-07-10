package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler

/** No deferred trash-cleanup scheduler on desktop. */
class JvmTrashCleanupScheduler : TrashCleanupScheduler {
    override suspend fun scheduleCleanup() = Unit
    override suspend fun cancelCleanup() = Unit
}
