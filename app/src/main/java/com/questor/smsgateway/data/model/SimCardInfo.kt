package com.questor.smsgateway.data.model

data class SimCardInfo(
    val slotIndex: Int, // 0-based: 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val countryIso: String,
    val isDataRoaming: Boolean = false,
    val signalStrengthLevel: Int = 4, // 0 - 4
    val signalDbm: String = "Good"
)
