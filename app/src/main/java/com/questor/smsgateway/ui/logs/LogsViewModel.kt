package com.questor.smsgateway.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.GatewayLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel : ViewModel() {

    private val app = GatewayApp.instance
    val filterLevel = MutableStateFlow<String?>("ALL")

    val logsState: StateFlow<List<GatewayLogEntity>> = filterLevel
        .flatMapLatest { level ->
            if (level == null || level == "ALL") {
                app.loggerRepository.getRecentLogsFlow(300)
            } else {
                app.loggerRepository.getLogsByLevelFlow(level, 300)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun setFilter(level: String) {
        filterLevel.value = level
    }

    fun clearLogs() {
        viewModelScope.launch {
            app.loggerRepository.clearLogs()
        }
    }
}
