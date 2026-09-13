package com.questor.smsgateway.data.repository

import android.util.Log
import com.questor.smsgateway.data.db.dao.GatewayLogDao
import com.questor.smsgateway.data.db.entities.GatewayLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class LoggerRepository(
    private val logDao: GatewayLogDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        const val THIRTY_MINUTES_MS = 30 * 60 * 1000L
        const val MAX_RETAINED_LOGS = 50
    }

    init {
        startPeriodicPruning()
    }

    private fun startPeriodicPruning() {
        scope.launch {
            while (true) {
                delay(5 * 60 * 1000L) // Run periodic prune every 5 minutes
                pruneLogs()
            }
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("INFO", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message (${throwable.message})" else message
        Log.w(tag, msg, throwable)
        write("WARN", tag, msg)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message (${throwable.message})" else message
        Log.e(tag, msg, throwable)
        write("ERROR", tag, msg)
    }

    private fun write(level: String, tag: String, message: String) {
        scope.launch {
            try {
                logDao.insert(
                    GatewayLogEntity(
                        level = level,
                        tag = tag,
                        message = message
                    )
                )
                val cutoffUtc = System.currentTimeMillis() - THIRTY_MINUTES_MS
                logDao.pruneOldLogs(cutoffUtc, keepCount = MAX_RETAINED_LOGS)
            } catch (_: Exception) {}
        }
    }

    suspend fun pruneLogs() {
        try {
            val cutoffUtc = System.currentTimeMillis() - THIRTY_MINUTES_MS
            logDao.pruneOldLogs(cutoffUtc, keepCount = MAX_RETAINED_LOGS)
        } catch (_: Exception) {}
    }

    fun getRecentLogsFlow(limit: Int = 300): Flow<List<GatewayLogEntity>> = logDao.getRecentLogsFlow(limit)
    fun getLogsByLevelFlow(level: String, limit: Int = 300): Flow<List<GatewayLogEntity>> = logDao.getLogsByLevelFlow(level, limit)

    suspend fun clearLogs() = logDao.clearAll()
}

