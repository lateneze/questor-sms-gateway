package com.questor.smsgateway.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "inbound_messages")
data class InboundMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromPhone: String,
    val messageText: String,
    val simSlot: Int? = null,
    val receivedAtUtc: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false
)
