package com.questor.smsgateway.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class GatewayHealthResponse(
    val isReady: Boolean,
    val model: String? = null,
    val carrier: String? = null,
    val signalStrength: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val serverUptimeSeconds: Long? = null,
    val pendingCount: Int = 0,
    val sentCount: Int = 0,
    val deliveredCount: Int = 0,
    val failedCount: Int = 0,
    val inboundCount: Int = 0,
    val transport: String? = null,
    val error: String? = null
)
