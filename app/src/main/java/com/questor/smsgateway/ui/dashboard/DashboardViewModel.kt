package com.questor.smsgateway.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import com.questor.smsgateway.data.model.GatewaySettings
import com.questor.smsgateway.data.model.SimCardInfo
import com.questor.smsgateway.server.KtorHttpServer
import com.questor.smsgateway.service.GatewayForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class DashboardUiState(
    val isServiceRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val serverPort: Int = 8765,
    val activeTransport: String = "USB Tethering",
    val simCards: List<SimCardInfo> = emptyList(),
    val preferredSimSlot: Int = 0,
    val pendingCount: Int = 0,
    val sentCount: Int = 0,
    val deliveredCount: Int = 0,
    val failedCount: Int = 0,
    val inboundCount: Int = 0,
    val isWebSocketConnected: Boolean = false
)

class DashboardViewModel : ViewModel() {

    private val app = GatewayApp.instance
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
        startPeriodicRefresh()
    }

    private fun loadData() {
        viewModelScope.launch {
            app.settingsRepository.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    serverPort = settings.serverPort,
                    activeTransport = settings.activeTransport.displayName,
                    preferredSimSlot = settings.preferredSimSlot
                )
            }
        }

        viewModelScope.launch {
            app.gatewayRepository.pendingCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(pendingCount = count)
            }
        }

        viewModelScope.launch {
            app.gatewayRepository.sentCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(sentCount = count)
            }
        }

        viewModelScope.launch {
            app.gatewayRepository.deliveredCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(deliveredCount = count)
            }
        }

        viewModelScope.launch {
            app.gatewayRepository.failedCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(failedCount = count)
            }
        }

        viewModelScope.launch {
            app.gatewayRepository.inboundCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(inboundCount = count)
            }
        }
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                val isRunning = GatewayForegroundService.isServiceRunning
                val ip = KtorHttpServer.getLocalIpAddress()
                val sims = app.multiSimManager.getActiveSimCards()
                val wsActive = app.webSocketHub.hasActiveSessions()

                _uiState.value = _uiState.value.copy(
                    isServiceRunning = isRunning,
                    localIp = ip,
                    simCards = sims,
                    isWebSocketConnected = wsActive
                )
                delay(2000L)
            }
        }
    }

    fun toggleService(context: Context) {
        if (GatewayForegroundService.isServiceRunning) {
            GatewayForegroundService.stopService(context)
            _uiState.value = _uiState.value.copy(isServiceRunning = false)
        } else {
            GatewayForegroundService.startService(context)
            _uiState.value = _uiState.value.copy(isServiceRunning = true)
        }
    }

    fun sendTestSms(toPhone: String, messageText: String) {
        viewModelScope.launch {
            val msgId = "TEST-${UUID.randomUUID().toString().take(8).uppercase()}"
            val entity = OutboxMessageEntity(
                messageId = msgId,
                toPhone = toPhone,
                messageText = messageText,
                simSlot = _uiState.value.preferredSimSlot,
                status = "PENDING"
            )
            app.gatewayRepository.enqueueOutboxMessage(entity)
            app.loggerRepository.i("DashboardViewModel", "Enqueued test SMS $msgId to $toPhone")
        }
    }

    fun resetQueueStats() {
        viewModelScope.launch {
            app.gatewayRepository.resetAllQueueStats()
            app.loggerRepository.i("DashboardViewModel", "All message queue stats and records reset")
        }
    }

    fun clearCompletedHistory() {
        viewModelScope.launch {
            app.gatewayRepository.clearCompletedHistory()
            app.loggerRepository.i("DashboardViewModel", "Completed message history cleared")
        }
    }
}
