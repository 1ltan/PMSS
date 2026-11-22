package com.example.lr_1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorRecordDao {
    @Insert
    suspend fun insert(record: SensorRecord): Long

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM sensor_records
            ORDER BY timestamp DESC, id DESC
            LIMIT :limit
        )
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun observeLatest(limit: Int): Flow<List<SensorRecord>>

    @Query("SELECT COUNT(*) FROM sensor_records WHERE synced = 0")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM sensor_records
        WHERE synced = 0
        ORDER BY timestamp ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun pending(limit: Int): List<SensorRecord>

    @Query("UPDATE sensor_records SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM sensor_records")
    suspend fun deleteAll()
}
