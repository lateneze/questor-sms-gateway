# Questor SMS Gateway (Android Companion App)

A dedicated, high-reliability Android SMS Gateway appliance built for **Questor School Manager**.

---

## Key Features

- **Appliance-Grade Reliability**: Runs continuously in the background as an Android 14/15 `remoteMessaging` foreground service with partial WakeLock and WifiLock.
- **Multiple Physical & Network Transports**:
  - **USB Tethering** (Primary USB mode: standard TCP/IP over USB cable without ADB requirements).
  - **Wi-Fi / LAN / Hotspot / Wi-Fi Direct** (Local network with auto-discovery via mDNS Zeroconf `_questorgw._tcp.`).
  - **Bluetooth RFCOMM** (Direct serial socket communication on UUID `7b5e1ad3-5ce2-4e52-9f5b-fba319d2d6b0`).
  - **Android Open Accessory (AOA 2.0)** (Direct USB streaming fallback).
- **Hybrid REST + WebSocket Protocol**:
  - REST endpoints on port `8765` for SMS dispatching, health probes, and inbound sync.
  - Real-time bidirectional WebSocket channel (`/api/v1/gateway/ws`) for instant push of incoming SMS and live delivery status receipts.
- **Multi-SIM & Telephony Support**:
  - Multi-SIM detection (`SubscriptionManager`) with live carrier names and signal meters.
  - Preferred SIM slot selection (Default / SIM 1 / SIM 2) and per-message SIM targeting.
- **Smart SMS Delivery & Anti-Throttling**:
  - Configurable rate limiting (1.5s – 3.0s delay between dispatches) to protect SIM cards from carrier throttling.
  - Multi-part concatenated SMS support with individual part tracking and carrier delivery receipts.
- **Persistent Local Queue**:
  - Room SQLite database (`outbox_messages`, `inbound_messages`, `delivery_reports`, `gateway_logs`).
  - Safe against network interruptions, cable disconnections, and app restarts.
- **Modern Jetpack Compose UI**:
  - Live status dashboard with start/stop toggle, IP/port display, real-time counters (Pending, Sent, Delivered, Inbound, Failed), and test SMS dispatcher.
  - Diagnostics and real-time scrolling log viewer with export capability.

---

## Architecture

```
com.questor.smsgateway/
├── data/
│   ├── db/          (Room Database, Entities, DAOs)
│   ├── model/       (Data models & Enums)
│   └── repository/  (GatewayRepository, SettingsRepository, LoggerRepository)
├── service/         (GatewayForegroundService, BootReceiver, WakeLockManager)
├── server/          (KtorHttpServer, WebSocketHub, NsdDiscoveryBroadcaster, BluetoothServer, AoaUsbServer, DTOs)
├── telephony/       (MultiSimManager, SmsDispatcher, SmsSentReceiver, SmsDeliveredReceiver, SmsIncomingReceiver)
└── ui/              (Jetpack Compose UI: Dashboard, Settings, Logs, Theme, MainActivity)
```

---

## Build Instructions

To build the APK:
```bash
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Setup & Deployment Guide for Schools

1. **Install the APK** on the Android smartphone.
2. **Grant Required Permissions**: SMS (Send/Receive/Read), Phone State, and Notifications.
3. **Disable Battery Optimizations**: Tap "Disable Battery Optimization" in the Settings tab so Android never kills the service when the screen is locked.
4. **Choose Connection Method**:
   - **USB Cable**: Plug the phone into the Questor PC and turn on **USB Tethering** in Android Settings.
   - **Wi-Fi**: Connect the phone to the school Wi-Fi or phone hotspot.
5. **Start Service**: Tap **Start** in the Dashboard.
6. **In Questor School Manager**: Register the gateway using the displayed IP and Port (default: `8765`).