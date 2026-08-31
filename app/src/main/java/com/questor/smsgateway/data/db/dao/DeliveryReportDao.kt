package com.questor.smsgateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questor.smsgateway.data.db.entities.DeliveryReportEntity

@Dao
interface DeliveryReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(report: DeliveryReportEntity)

    @Query("SELECT * FROM delivery_reports WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): DeliveryReportEntity?

    @Query("SELECT * FROM delivery_reports WHERE messageId IN (:messageIds)")
    suspend fun getByMessageIds(messageIds: List<String>): List<DeliveryReportEntity>

    @Query("UPDATE delivery_reports SET isAcknowledged = 1 WHERE messageId IN (:messageIds)")
    suspend fun markAcknowledged(messageIds: List<String>): Int

    @Query("DELETE FROM delivery_reports WHERE isAcknowledged = 1 AND updatedAtUtc < :beforeUtc")
    suspend fun cleanupOldAcknowledged(beforeUtc: Long)
}
