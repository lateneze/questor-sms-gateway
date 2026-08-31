package com.questor.smsgateway.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_messages")
data class OutboxMessageEntity(
    @PrimaryKey val messageId: String,
    val toPhone: String,
    val messageText: String,
    val simSlot: Int? = null,
    val status: String = "PENDING", // PENDING, SENDING, SENT, DELIVERED, FAILED
    val retryCount: Int = 0,
    val partsTotal: Int = 1,
    val partsSent: Int = 0,
    val errorMessage: String? = null,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val sentAtUtc: Long? = null,
    val deliveredAtUtc: Long? = null
)
