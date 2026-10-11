package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import kotlin.time.Instant

/** An in-memory queue without the dedup rules - tests read [operations] to see what was queued. */
class FakePendingSyncDataSource : PendingSyncDataSource {

    val operations = mutableListOf<PendingSyncOperation>()

    override suspend fun getPendingOperations(): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(operations.toList())

    override suspend fun getOperationsByEntityId(
        entityId: String,
    ): Result<List<PendingSyncOperation>, DataError.Local> =
        Result.Success(operations.filter { it.entityId == entityId })

    override suspend fun hasPendingCreate(entityId: String): Result<Boolean, DataError.Local> =
        Result.Success(operations.any { it.entityId == entityId && it.operationType == PendingSyncOperation.OP_CREATE })

    override suspend fun enqueue(
        entityId: String,
        entityType: String,
        operationType: String,
        parentEntityId: String?,
        createdAt: Instant,
    ): EmptyResult<DataError.Local> {
        operations +=
            PendingSyncOperation(
                operationId = "op-${operations.size}",
                entityId = entityId,
                entityType = entityType,
                operationType = operationType,
                createdAt = createdAt,
                parentEntityId = parentEntityId,
            )
        return Result.Success(Unit)
    }

    override suspend fun deleteOperation(operationId: String): EmptyResult<DataError.Local> {
        operations.removeAll { it.operationId == operationId }
        return Result.Success(Unit)
    }

    override suspend fun deleteOperationsByEntityId(entityId: String): EmptyResult<DataError.Local> {
        operations.removeAll { it.entityId == entityId }
        return Result.Success(Unit)
    }
}
