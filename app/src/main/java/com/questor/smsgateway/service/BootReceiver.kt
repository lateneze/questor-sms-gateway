package com.questor.smsgateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.questor.smsgateway.GatewayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val app = context.applicationContext as GatewayApp
            CoroutineScope(Dispatchers.IO).launch {
                val settings = app.settingsRepository.settingsFlow.first()
                if (settings.autoStartOnBoot) {
                    app.loggerRepository.i("BootReceiver", "Device reboot detected. Auto-starting SMS Gateway service.")
                    GatewayForegroundService.startService(context)
                }
            }
        }
    }
}
