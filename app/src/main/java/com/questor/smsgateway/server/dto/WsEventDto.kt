package com.questor.smsgateway.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class WsIncomingEvent(
    val event: String = "incoming_sms",
    val data: InboundSmsDto
)

@Serializable
data class WsDeliveryEvent(
    val event: String = "delivery_update",
    val data: DeliveryStatusDto
)

@Serializable
data class WsHeartbeatEvent(
    val event: String = "heartbeat",
    val timestamp: Long = System.currentTimeMillis()
)
