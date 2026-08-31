package com.questor.smsgateway.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryStatusDto(
    val messageId: String,
    val status: String, // "delivered", "failed", "sent", "pending"
    val detail: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class DeliveryBatchRequest(
    val messageIds: List<String>
)

@Serializable
data class DeliveryBatchResponse(
    val results: List<DeliveryStatusDto>
)

@Serializable
data class DeliveryAckRequest(
    val messageIds: List<String>
)

@Serializable
data class DeliveryAckResponse(
    val acknowledgedCount: Int
)
