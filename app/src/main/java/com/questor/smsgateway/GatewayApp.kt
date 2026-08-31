package com.questor.smsgateway

import android.app.Application
import com.questor.smsgateway.data.db.GatewayDatabase
import com.questor.smsgateway.data.repository.GatewayRepository
import com.questor.smsgateway.data.repository.LoggerRepository
import com.questor.smsgateway.data.repository.SettingsRepository
import com.questor.smsgateway.server.WebSocketHub
import com.questor.smsgateway.telephony.MultiSimManager

class GatewayApp : Application() {

    lateinit var database: GatewayDatabase
        private set

    lateinit var gatewayRepository: GatewayRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var loggerRepository: LoggerRepository
        private set

    lateinit var multiSimManager: MultiSimManager
        private set

    lateinit var webSocketHub: WebSocketHub
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = GatewayDatabase.getInstance(this)
        loggerRepository = LoggerRepository(database.gatewayLogDao())
        gatewayRepository = GatewayRepository(database)
        settingsRepository = SettingsRepository(this)
        multiSimManager = MultiSimManager(this)
        webSocketHub = WebSocketHub()

        loggerRepository.i("GatewayApp", "Questor SMS Gateway Application initialized")
    }

    companion object {
        lateinit var instance: GatewayApp
            private set
    }
}
