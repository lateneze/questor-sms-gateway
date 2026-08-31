package com.questor.smsgateway.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class InboundSmsDto(
    val id: String,
    val from: String,
    val message: String,
    val receivedAt: String,
    val simSlot: Int? = null
)

@Serializable
data class InboundBatchResponse(
    val messages: List<InboundSmsDto>
)

@Serializable
data class InboundAckRequest(
    val messageIds: List<String>
)

@Serializable
data class InboundAckResponse(
    val success: Boolean,
    val acknowledgedCount: Int
)
