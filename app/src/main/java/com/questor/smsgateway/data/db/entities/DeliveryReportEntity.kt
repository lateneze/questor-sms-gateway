package com.questor.smsgateway.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_reports")
data class DeliveryReportEntity(
    @PrimaryKey val messageId: String,
    val status: String, // delivered, failed, sent
    val detail: String? = null,
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false
)
