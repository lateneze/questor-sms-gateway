package com.questor.smsgateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questor.smsgateway.data.db.entities.InboundMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InboundDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: InboundMessageEntity)

    @Query("SELECT * FROM inbound_messages WHERE isAcknowledged = 0 ORDER BY receivedAtUtc ASC LIMIT :limit")
    suspend fun getUnacknowledged(limit: Int = 100): List<InboundMessageEntity>

    @Query("UPDATE inbound_messages SET isAcknowledged = 1 WHERE id IN (:ids)")
    suspend fun markAcknowledged(ids: List<String>)

    @Query("SELECT COUNT(*) FROM inbound_messages WHERE isAcknowledged = 0")
    fun countUnacknowledgedFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inbound_messages")
    fun countTotalInboundFlow(): Flow<Int>

    @Query("SELECT * FROM inbound_messages ORDER BY receivedAtUtc DESC LIMIT :limit")
    fun getRecentInboundFlow(limit: Int = 100): Flow<List<InboundMessageEntity>>

    @Query("DELETE FROM inbound_messages WHERE isAcknowledged = 1 AND receivedAtUtc < :beforeUtc")
    suspend fun cleanupOldAcknowledged(beforeUtc: Long)

    @Query("DELETE FROM inbound_messages WHERE isAcknowledged = 1")
    suspend fun clearAcknowledged()

    @Query("DELETE FROM inbound_messages")
    suspend fun clearAll()
}
