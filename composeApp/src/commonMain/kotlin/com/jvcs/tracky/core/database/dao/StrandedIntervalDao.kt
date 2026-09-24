package com.jvcs.tracky.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Intervals found open at start-up that nothing is timing.
 *
 * Local-only, never synced. An interval listed here is open but nothing is timing it, so every
 * "what is currently open" interval query excludes it and no aggregation counts it - an open
 * interval banks nothing until it closes. See StrandedIntervalEntity.
 */
@Dao
interface StrandedIntervalDao {

    @Upsert
    suspend fun upsertStrandedInterval(stranded: StrandedIntervalEntity)

    @Query("DELETE FROM stranded_intervals WHERE intervalId = :intervalId")
    suspend fun deleteStrandedInterval(intervalId: String)

    @Query("SELECT * FROM stranded_intervals WHERE intervalId = :intervalId")
    suspend fun getStrandedInterval(intervalId: String): StrandedIntervalEntity?

    @Query("SELECT * FROM stranded_intervals ORDER BY detectedAtEpochMs ASC")
    fun observeStrandedIntervals(): Flow<List<StrandedIntervalEntity>>
}
