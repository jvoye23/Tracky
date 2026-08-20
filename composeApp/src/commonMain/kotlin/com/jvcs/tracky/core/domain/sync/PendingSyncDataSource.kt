package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlin.time.Instant

/**
 * The pending-sync queue, seen from the domain.
 *
 * Every repository enqueues through here instead of touching the DAO, so the queue keeps one set
 * of dedup rules and one place where a database failure becomes a [DataError.Local].
 */
interface PendingSyncDataSource {
    /** Oldest first — the drain order is what keeps a CREATE ahead of a later UPDATE. */
    suspend fun getPendingOperations(): Result<List<PendingSyncOperation>, DataError.Local>

    suspend fun getOperationsByEntityId(entityId: String): Result<List<PendingSyncOperation>, DataError.Local>

    /**
     * True when [entityId] still has a queued CREATE — i.e. the entity exists only on this device.
     *
     * This is what the sync ordering hangs on: a child cannot be pushed while its parent has no
     * server-side id to hang off.
     */
    suspend fun hasPendingCreate(entityId: String): Result<Boolean, DataError.Local>

    /** Merges the op into the queue under the existing dedup rules; the operation id is generated here. */
    suspend fun enqueue(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?,
        createdAt: Instant
    ): EmptyResult<DataError.Local>

    suspend fun deleteOperation(operationId: String): EmptyResult<DataError.Local>

    suspend fun deleteOperationsByEntityId(entityId: String): EmptyResult<DataError.Local>
}
