package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {
    @Upsert
    suspend fun upsertOperation(operation: PendingSyncEntity)

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
