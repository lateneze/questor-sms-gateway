package com.questor.smsgateway.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.data.db.entities.DeliveryReportEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("EXTRA_MESSAGE_ID") ?: return
        val resultCode = resultCode

        val app = context.applicationContext as GatewayApp
        val gatewayRepo = app.gatewayRepository
        val logger = app.loggerRepository
        val wsHub = app.webSocketHub

        CoroutineScope(Dispatchers.IO).launch {
            val status = if (resultCode == Activity.RESULT_OK) "delivered" else "failed"
            val detail = if (resultCode == Activity.RESULT_OK) "Delivered to handset" else "Delivery failed code: $resultCode"

            gatewayRepo.recordDeliveryReport(
                DeliveryReportEntity(
                    messageId = messageId,
                    status = status,
                    detail = detail
                )
            )

            if (status == "delivered") {
                logger.i("SmsDeliveredReceiver", "Message $messageId delivered to recipient handset")
            } else {
                logger.w("SmsDeliveredReceiver", "Message $messageId delivery report indicated failure ($detail)")
            }

            wsHub.broadcastDeliveryUpdate(messageId, status, detail)
        }
    }
}
