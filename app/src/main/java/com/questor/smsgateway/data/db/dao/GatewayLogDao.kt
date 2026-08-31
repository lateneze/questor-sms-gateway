package com.questor.smsgateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.questor.smsgateway.data.db.entities.GatewayLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GatewayLogDao {
    @Insert
    suspend fun insert(log: GatewayLogEntity)

    @Query("SELECT * FROM gateway_logs ORDER BY timestampUtc DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 300): Flow<List<GatewayLogEntity>>

    @Query("SELECT * FROM gateway_logs WHERE level = :level ORDER BY timestampUtc DESC LIMIT :limit")
    fun getLogsByLevelFlow(level: String, limit: Int = 300): Flow<List<GatewayLogEntity>>

    @Query("DELETE FROM gateway_logs")
    suspend fun clearAll()

    @Query("DELETE FROM gateway_logs WHERE id NOT IN (SELECT id FROM gateway_logs ORDER BY timestampUtc DESC LIMIT 1000)")
    suspend fun trimOldLogs()
}
