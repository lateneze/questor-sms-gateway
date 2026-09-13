package com.questor.smsgateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT COUNT(*) FROM gateway_logs WHERE timestampUtc >= :cutoffUtc")
    suspend fun countLogsNewerThan(cutoffUtc: Long): Int

    @Query("DELETE FROM gateway_logs WHERE timestampUtc < :cutoffUtc")
    suspend fun deleteLogsOlderThan(cutoffUtc: Long)

    @Query("DELETE FROM gateway_logs WHERE id NOT IN (SELECT id FROM gateway_logs ORDER BY timestampUtc DESC LIMIT :keepCount)")
    suspend fun trimBeyondLatest(keepCount: Int = 50)

    @Transaction
    suspend fun pruneOldLogs(cutoffUtc: Long, keepCount: Int = 50) {
        val recentCount = countLogsNewerThan(cutoffUtc)
        if (recentCount > 0) {
            deleteLogsOlderThan(cutoffUtc)
        } else {
            trimBeyondLatest(keepCount)
        }
    }

    @Query("DELETE FROM gateway_logs WHERE id NOT IN (SELECT id FROM gateway_logs ORDER BY timestampUtc DESC LIMIT 1000)")
    suspend fun trimOldLogs()
}

