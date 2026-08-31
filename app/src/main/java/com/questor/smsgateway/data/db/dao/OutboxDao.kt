package com.questor.smsgateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutboxMessageEntity)

    @Update
    suspend fun update(message: OutboxMessageEntity)

    @Query("SELECT * FROM outbox_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getById(messageId: String): OutboxMessageEntity?

    @Query("SELECT * FROM outbox_messages WHERE status = 'PENDING' ORDER BY createdAtUtc ASC LIMIT 1")
    suspend fun getNextPending(): OutboxMessageEntity?

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'PENDING'")
    fun countPendingFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'SENT'")
    fun countSentFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'DELIVERED'")
    fun countDeliveredFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'FAILED'")
    fun countFailedFlow(): Flow<Int>

    @Query("SELECT * FROM outbox_messages ORDER BY createdAtUtc DESC LIMIT :limit")
    fun getRecentMessagesFlow(limit: Int = 100): Flow<List<OutboxMessageEntity>>

    @Query("DELETE FROM outbox_messages WHERE status IN ('SENT', 'DELIVERED') AND createdAtUtc < :beforeUtc")
    suspend fun cleanupOldCompleted(beforeUtc: Long)

    @Query("DELETE FROM outbox_messages WHERE status != 'PENDING'")
    suspend fun clearCompleted()

    @Query("DELETE FROM outbox_messages")
    suspend fun clearAll()
}
