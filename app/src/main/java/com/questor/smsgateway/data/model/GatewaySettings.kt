package com.questor.smsgateway.data.model

data class GatewaySettings(
    val serverPort: Int = 8765,
    val gatewayKey: String = "",
    val activeTransport: TransportMode = TransportMode.USB_TETHER,
    val preferredSimSlot: Int = 0, // 0 = Default, 1 = SIM 1, 2 = SIM 2
    val rateLimitDelayMs: Long = 1500L, // 1.5 seconds between SMS dispatches
    val autoStartOnBoot: Boolean = true,
    val acquireWakeLock: Boolean = true
)
