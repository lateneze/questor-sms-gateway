package com.questor.smsgateway.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.InboundMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsIncomingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val app = context.applicationContext as GatewayApp
        val gatewayRepo = app.gatewayRepository
        val logger = app.loggerRepository
        val wsHub = app.webSocketHub

        // Group multipart SMS chunks by sender phone number
        val messagesBySender = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "Unknown" }

        CoroutineScope(Dispatchers.IO).launch {
            for ((sender, parts) in messagesBySender) {
                val fullBody = parts.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
                val slot = intent.getIntExtra("slot", 0)

                val inbound = InboundMessageEntity(
                    fromPhone = sender,
                    messageText = fullBody,
                    simSlot = slot,
                    receivedAtUtc = System.currentTimeMillis()
                )

                gatewayRepo.recordInboundMessage(inbound)
                logger.i("SmsIncomingReceiver", "Received SMS from $sender (${fullBody.length} chars)")
                wsHub.broadcastIncomingSms(inbound)
            }
        }
    }
}
