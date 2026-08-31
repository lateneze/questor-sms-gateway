package com.questor.smsgateway.data.model

enum class TransportMode(val displayName: String) {
    USB_TETHER("USB Tethering (Network)"),
    WIFI_LAN("Wi-Fi / LAN"),
    BLUETOOTH("Bluetooth RFCOMM"),
    USB_AOA("USB Direct (AOA)")
}
