package com.questor.smsgateway.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsRequest(
    val messageId: String,
    val to: String,
    val message: String,
    val simSlot: Int? = null
)

@Serializable
data class SendSmsResponse(
    val messageId: String,
    val status: String, // "accepted", "queued", "error"
    val error: String? = null
)
