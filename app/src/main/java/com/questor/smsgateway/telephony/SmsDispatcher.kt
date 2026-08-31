package com.questor.smsgateway.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import com.questor.smsgateway.data.repository.GatewayRepository
import com.questor.smsgateway.data.repository.LoggerRepository
import com.questor.smsgateway.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmsDispatcher(
    private val context: Context,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository,
    private val simManager: MultiSimManager,
    private val logger: LoggerRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var workerJob: Job? = null
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        workerJob = scope.launch {
            logger.i("SmsDispatcher", "SMS Dispatcher background queue worker started")
            while (isActive && isRunning) {
                try {
                    val pendingMsg = gatewayRepo.getNextPendingOutboxMessage()
                    if (pendingMsg != null) {
                        dispatchMessage(pendingMsg)
                        val settings = settingsRepo.settingsFlow.first()
                        delay(settings.rateLimitDelayMs.coerceAtLeast(500L))
                    } else {
                        delay(1000L) // Idle polling delay when no messages are pending
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.e("SmsDispatcher", "Unexpected error in queue loop", e)
                    delay(3000L)
                }
            }
            logger.i("SmsDispatcher", "SMS Dispatcher background queue worker stopped")
        }
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
    }

    private suspend fun dispatchMessage(message: OutboxMessageEntity) {
        try {
            logger.i("SmsDispatcher", "Dispatching SMS ${message.messageId} to ${message.toPhone}")

            val settings = settingsRepo.settingsFlow.first()
            val targetSlot = message.simSlot ?: settings.preferredSimSlot
            val subId = simManager.getSubscriptionIdForSlot(targetSlot)

            val smsManager: SmsManager = if (subId != null && subId > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            val parts = smsManager.divideMessage(message.messageText)
            val totalParts = parts.size

            // Update outbox state to SENDING
            gatewayRepo.updateOutboxMessage(
                message.copy(
                    status = "SENDING",
                    partsTotal = totalParts,
                    partsSent = 0
                )
            )

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            for (i in 0 until totalParts) {
                val sentIntent = Intent("com.questor.smsgateway.SMS_SENT").apply {
                    setPackage(context.packageName)
                    putExtra("EXTRA_MESSAGE_ID", message.messageId)
                    putExtra("EXTRA_PART_INDEX", i + 1)
                    putExtra("EXTRA_TOTAL_PARTS", totalParts)
                }
                val sentPi = PendingIntent.getBroadcast(
                    context,
                    "${message.messageId}_sent_$i".hashCode(),
                    sentIntent,
                    flags
                )
                sentIntents.add(sentPi)

                val deliveryIntent = Intent("com.questor.smsgateway.SMS_DELIVERED").apply {
                    setPackage(context.packageName)
                    putExtra("EXTRA_MESSAGE_ID", message.messageId)
                    putExtra("EXTRA_PART_INDEX", i + 1)
                    putExtra("EXTRA_TOTAL_PARTS", totalParts)
                }
                val delPi = PendingIntent.getBroadcast(
                    context,
                    "${message.messageId}_del_$i".hashCode(),
                    deliveryIntent,
                    flags
                )
                deliveryIntents.add(delPi)
            }

            if (totalParts == 1) {
                smsManager.sendTextMessage(
                    message.toPhone,
                    null,
                    parts[0],
                    sentIntents[0],
                    deliveryIntents[0]
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    message.toPhone,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }

            logger.i("SmsDispatcher", "Handed off ${message.messageId} ($totalParts part(s)) to radio stack")
        } catch (e: Exception) {
            logger.e("SmsDispatcher", "Failed to dispatch SMS ${message.messageId}", e)
            gatewayRepo.updateOutboxMessage(
                message.copy(
                    status = "FAILED",
                    errorMessage = e.message ?: "Failed to dispatch"
                )
            )
        }
    }
}
