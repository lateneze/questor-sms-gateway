package com.questor.smsgateway.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.questor.smsgateway.data.model.GatewaySettings
import com.questor.smsgateway.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gateway_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_PORT = intPreferencesKey("server_port")
        val GATEWAY_KEY = stringPreferencesKey("gateway_key")
        val ACTIVE_TRANSPORT = stringPreferencesKey("active_transport")
        val PREFERRED_SIM_SLOT = intPreferencesKey("preferred_sim_slot")
        val RATE_LIMIT_DELAY_MS = longPreferencesKey("rate_limit_delay_ms")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val ACQUIRE_WAKE_LOCK = booleanPreferencesKey("acquire_wake_lock")
    }

    val settingsFlow: Flow<GatewaySettings> = context.dataStore.data.map { prefs ->
        val transportStr = prefs[Keys.ACTIVE_TRANSPORT] ?: TransportMode.USB_TETHER.name
        val transport = try {
            TransportMode.valueOf(transportStr)
        } catch (_: Exception) {
            TransportMode.USB_TETHER
        }

        GatewaySettings(
            serverPort = prefs[Keys.SERVER_PORT] ?: 8765,
            gatewayKey = prefs[Keys.GATEWAY_KEY] ?: "",
            activeTransport = transport,
            preferredSimSlot = prefs[Keys.PREFERRED_SIM_SLOT] ?: 0,
            rateLimitDelayMs = prefs[Keys.RATE_LIMIT_DELAY_MS] ?: 1500L,
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: true,
            acquireWakeLock = prefs[Keys.ACQUIRE_WAKE_LOCK] ?: true
        )
    }

    suspend fun updateSettings(settings: GatewaySettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_PORT] = settings.serverPort
            prefs[Keys.GATEWAY_KEY] = settings.gatewayKey
            prefs[Keys.ACTIVE_TRANSPORT] = settings.activeTransport.name
            prefs[Keys.PREFERRED_SIM_SLOT] = settings.preferredSimSlot
            prefs[Keys.RATE_LIMIT_DELAY_MS] = settings.rateLimitDelayMs
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.ACQUIRE_WAKE_LOCK] = settings.acquireWakeLock
        }
    }

    suspend fun setActiveTransport(mode: TransportMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_TRANSPORT] = mode.name
        }
    }

    suspend fun setPreferredSimSlot(slot: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_SIM_SLOT] = slot
        }
    }

    suspend fun setRateLimitDelayMs(delayMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RATE_LIMIT_DELAY_MS] = delayMs
        }
    }
}
