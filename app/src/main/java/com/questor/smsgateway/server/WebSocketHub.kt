package com.questor.smsgateway.server

import com.questor.smsgateway.data.db.entities.InboundMessageEntity
import com.questor.smsgateway.server.dto.InboundSmsDto
import com.questor.smsgateway.server.dto.WsDeliveryEvent
import com.questor.smsgateway.server.dto.WsIncomingEvent
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections

class WebSocketHub(private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {

    private val sessions = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun addSession(session: DefaultWebSocketSession) {
        sessions.add(session)
    }

    fun removeSession(session: DefaultWebSocketSession) {
        sessions.remove(session)
    }

    fun hasActiveSessions(): Boolean = sessions.isNotEmpty()

    fun broadcastIncomingSms(inbound: InboundMessageEntity) {
        val payload = WsIncomingEvent(
            event = "incoming_sms",
            data = InboundSmsDto(
                id = inbound.id,
                from = inbound.fromPhone,
                message = inbound.messageText,
                receivedAt = inbound.receivedAtUtc.toString(),
                simSlot = inbound.simSlot
            )
        )
        sendJson(json.encodeToString(payload))
    }

    fun broadcastDeliveryUpdate(messageId: String, status: String, detail: String? = null) {
        val payload = WsDeliveryEvent(
            event = "delivery_update",
            data = com.questor.smsgateway.server.dto.DeliveryStatusDto(
                messageId = messageId,
                status = status,
                detail = detail,
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        sendJson(json.encodeToString(payload))
    }

    private fun sendJson(text: String) {
        scope.launch {
            val deadSessions = mutableListOf<DefaultWebSocketSession>()
            sessions.forEach { session ->
                try {
                    session.send(Frame.Text(text))
                } catch (_: Exception) {
                    deadSessions.add(session)
                }
            }
            sessions.removeAll(deadSessions.toSet())
        }
    }
}
