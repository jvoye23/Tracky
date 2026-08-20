package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.database.dao.PendingSyncDao
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomPendingSyncDataSource(
    private val pendingSyncDao: PendingSyncDao
) : PendingSyncDataSource {

    override suspend fun getPendingOperations(): Result<List<PendingSyncOperation>, DataError.Local> =
        dbRead { pendingSyncDao.getAllPendingOperations().map { it.toPendingSyncOperation() } }

    override suspend fun getOperationsByEntityId(
        entityId: String
    ): Result<List<PendingSyncOperation>, DataError.Local> =
        dbRead { pendingSyncDao.getOperationsByEntityId(entityId).map { it.toPendingSyncOperation() } }

    override suspend fun hasPendingCreate(entityId: String): Result<Boolean, DataError.Local> =
        dbRead {
            pendingSyncDao.getOperationsByEntityId(entityId)
                .any { it.operationType == PendingSyncOperation.OP_CREATE }
        }

    override suspend fun enqueue(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?,
        createdAt: Instant
    ): EmptyResult<DataError.Local> = dbRead {
        // enqueueDeduped owns the merge rules (a pending CREATE absorbs later UPDATEs, a DELETE
        // cancels a pending CREATE outright) — this only supplies the row.
        pendingSyncDao.enqueueDeduped(
            PendingSyncEntity(
                operationId = Uuid.random().toString(),
                entityId = entityId,
                entityType = entityType,
                operationType = operationType,
                createdAtEpochMs = createdAt.toEpochMilliseconds(),
                parentEntityId = parentEntityId
            )
        )
    }

    override suspend fun deleteOperation(operationId: String): EmptyResult<DataError.Local> =
        dbRead { pendingSyncDao.deleteOperation(operationId) }

    override suspend fun deleteOperationsByEntityId(entityId: String): EmptyResult<DataError.Local> =
        dbRead { pendingSyncDao.deleteOperationsByEntityId(entityId) }

    /**
     * Turns a Room failure (corrupt or unavailable database) into a recoverable [DataError.Local]
     * instead of letting it crash the calling coroutine. Queue access is best-effort by nature:
     * losing it means a write syncs later, not that it is lost.
     */
    private inline fun <T> dbRead(block: () -> T): Result<T, DataError.Local> {
        return try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}

private fun PendingSyncEntity.toPendingSyncOperation() = PendingSyncOperation(
    operationId = operationId,
    entityId = entityId,
    entityType = entityType,
    operationType = operationType,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    parentEntityId = parentEntityId
)
