package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_CREATE
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_DELETE
import com.jvcs.tracky.core.database.entity.PendingSyncEntity.Companion.OP_UPDATE
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {
    @Upsert
    suspend fun upsertOperation(operation: PendingSyncEntity)

    // Atomically merge a new op into the queue for its entity. Running the read-decide-write
    // inside a single transaction prevents a concurrent enqueue/drain from racing in between.
    @Transaction
    suspend fun enqueueDeduped(op: PendingSyncEntity) {
        val existing = getOperationsByEntityId(op.entityId)
        when (op.operationType) {
            OP_CREATE -> upsertOperation(op)
            OP_UPDATE -> {
                // A pending CREATE/UPDATE already pushes the latest local state — no new op needed.
                val alreadyQueued = existing.any { it.operationType == OP_CREATE || it.operationType == OP_UPDATE }
                if (!alreadyQueued) upsertOperation(op)
            }
            OP_DELETE -> {
                val hadPendingCreate = existing.any { it.operationType == OP_CREATE }
                deleteOperationsByEntityId(op.entityId)
                if (!hadPendingCreate) upsertOperation(op)
            }
        }
    }

    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAtEpochMs ASC")
    fun getPendingOperations(): Flow<List<PendingSyncEntity>>

    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAtEpochMs ASC")
    suspend fun getAllPendingOperations(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync_operations WHERE entityId = :entityId ORDER BY createdAtEpochMs ASC")
    suspend fun getOperationsByEntityId(entityId: String): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync_operations WHERE operationId = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("DELETE FROM pending_sync_operations WHERE entityId = :entityId")
    suspend fun deleteOperationsByEntityId(entityId: String)

    @Query("DELETE FROM pending_sync_operations")
    suspend fun clear()
}
