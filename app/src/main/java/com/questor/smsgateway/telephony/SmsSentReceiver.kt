package com.questor.smsgateway.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.DeliveryReportEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("EXTRA_MESSAGE_ID") ?: return
        val partIndex = intent.getIntExtra("EXTRA_PART_INDEX", 0)
        val totalParts = intent.getIntExtra("EXTRA_TOTAL_PARTS", 1)
        val resultCode = resultCode

        val app = context.applicationContext as GatewayApp
        val gatewayRepo = app.gatewayRepository
        val logger = app.loggerRepository
        val wsHub = app.webSocketHub

        CoroutineScope(Dispatchers.IO).launch {
            val outbox = gatewayRepo.getOutboxMessage(messageId) ?: return@launch

            if (resultCode == Activity.RESULT_OK) {
                val newSentCount = outbox.partsSent + 1
                if (newSentCount >= totalParts) {
                    // All parts successfully sent
                    val updated = outbox.copy(
                        status = "SENT",
                        partsSent = newSentCount,
                        sentAtUtc = System.currentTimeMillis()
                    )
                    gatewayRepo.updateOutboxMessage(updated)
                    logger.i("SmsSentReceiver", "Message $messageId successfully sent all $totalParts parts")
                    wsHub.broadcastDeliveryUpdate(messageId, "sent", "Sent successfully to network")
                } else {
                    gatewayRepo.updateOutboxMessage(outbox.copy(partsSent = newSentCount))
                    logger.i("SmsSentReceiver", "Message $messageId sent part $partIndex of $totalParts")
                }
            } else {
                val errorReason = when (resultCode) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic carrier failure"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio/Cellular turned off"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS rate limit exceeded"
                    else -> "Carrier error code: $resultCode"
                }

                val updated = outbox.copy(
                    status = "FAILED",
                    errorMessage = errorReason
                )
                gatewayRepo.updateOutboxMessage(updated)
                gatewayRepo.recordDeliveryReport(
                    DeliveryReportEntity(
                        messageId = messageId,
                        status = "failed",
                        detail = errorReason
                    )
                )
                logger.e("SmsSentReceiver", "Message $messageId failed on part $partIndex: $errorReason")
                wsHub.broadcastDeliveryUpdate(messageId, "failed", errorReason)
            }
        }
    }
}
