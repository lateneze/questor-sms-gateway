package com.questor.smsgateway.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.questor.smsgateway.data.repository.LoggerRepository

class WakeLockManager(
    private val context: Context,
    private val logger: LoggerRepository
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun acquire() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null && powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "QuestorSmsGateway:WakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                logger.i("WakeLockManager", "Partial WakeLock acquired")
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null && wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "QuestorSmsGateway:WifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                logger.i("WakeLockManager", "High-perf WifiLock acquired")
            }

            if (multicastLock == null && wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock("QuestorSmsGateway:MdnsLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                logger.i("WakeLockManager", "MulticastLock acquired for mDNS")
            }
        } catch (e: Exception) {
            logger.w("WakeLockManager", "Failed to acquire locks", e)
        }
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                logger.i("WakeLockManager", "WakeLock released")
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                logger.i("WakeLockManager", "WifiLock released")
            }
            wifiLock = null

            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                logger.i("WakeLockManager", "MulticastLock released")
            }
            multicastLock = null
        } catch (e: Exception) {
            logger.w("WakeLockManager", "Failed to release locks", e)
        }
    }
}
