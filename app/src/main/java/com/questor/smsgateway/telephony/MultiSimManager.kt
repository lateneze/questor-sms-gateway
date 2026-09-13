package com.questor.smsgateway.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.questor.smsgateway.data.model.SimCardInfo

class MultiSimManager(private val context: Context) {

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun hasPhonePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getActiveSimCards(): List<SimCardInfo> {
        if (!hasPhonePermissions() || subscriptionManager == null) {
            return getDefaultSimFallback()
        }

        try {
            val subList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            if (subList.isNullOrEmpty()) {
                return getDefaultSimFallback()
            }

            return subList.map { info ->
                val slotIdx = info.simSlotIndex
                val subId = info.subscriptionId
                val carrier = info.carrierName?.toString() ?: info.displayName?.toString() ?: "Unknown Carrier"
                val country = info.countryIso?.uppercase() ?: ""
                val name = info.displayName?.toString() ?: "SIM ${slotIdx + 1}"

                SimCardInfo(
                    slotIndex = slotIdx,
                    subscriptionId = subId,
                    displayName = name,
                    carrierName = carrier,
                    countryIso = country,
                    isDataRoaming = info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE,
                    signalStrengthLevel = getSignalLevel(),
                    signalDbm = getSignalDescription()
                )
            }
        } catch (_: Exception) {
            return getDefaultSimFallback()
        }
    }

    fun isRealSubscriptionId(subId: Int?): Boolean {
        if (subId == null) return false
        if (subId <= 0) return false
        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) return false
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!SubscriptionManager.isValidSubscriptionId(subId)) return false
        }
        return true
    }

    fun getSubscriptionIdForSlot(preferredSlot: Int): Int? {
        val simList = getActiveSimCards()
        if (simList.isEmpty()) return null

        val candidate = when (preferredSlot) {
            1 -> simList.firstOrNull { it.slotIndex == 0 }?.subscriptionId
            2 -> simList.firstOrNull { it.slotIndex == 1 }?.subscriptionId
            else -> {
                val defaultSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    SubscriptionManager.getDefaultSmsSubscriptionId()
                } else {
                    @Suppress("DEPRECATION")
                    SubscriptionManager.getDefaultSubscriptionId()
                }
                if (isRealSubscriptionId(defaultSubId)) {
                    defaultSubId
                } else {
                    simList.firstOrNull()?.subscriptionId
                }
            }
        }

        return if (isRealSubscriptionId(candidate)) candidate else null
    }

    private fun getDefaultSimFallback(): List<SimCardInfo> {
        val networkOperator = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Cellular SIM"
        val country = telephonyManager?.networkCountryIso?.uppercase() ?: ""
        val defaultSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            @Suppress("DEPRECATION")
            SubscriptionManager.getDefaultSubscriptionId()
        }
        val safeSubId = if (isRealSubscriptionId(defaultSubId)) defaultSubId else -1

        return listOf(
            SimCardInfo(
                slotIndex = 0,
                subscriptionId = safeSubId,
                displayName = "Default SIM",
                carrierName = networkOperator,
                countryIso = country,
                signalStrengthLevel = getSignalLevel(),
                signalDbm = getSignalDescription()
            )
        )
    }

    private fun getSignalLevel(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telephonyManager != null) {
                telephonyManager.signalStrength?.level ?: 3
            } else {
                3
            }
        } catch (_: Exception) {
            3
        }
    }

    private fun getSignalDescription(): String {
        return when (getSignalLevel()) {
            4 -> "Excellent (Level 4/4)"
            3 -> "Good (Level 3/4)"
            2 -> "Moderate (Level 2/4)"
            1 -> "Weak (Level 1/4)"
            else -> "No Signal"
        }
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return "$manufacturer $model (Android ${Build.VERSION.RELEASE})"
    }
}
