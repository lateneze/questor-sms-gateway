package com.questor.smsgateway.server

import android.content.Context
import android.os.BatteryManager
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import com.questor.smsgateway.data.model.GatewaySettings
import com.questor.smsgateway.data.repository.GatewayRepository
import com.questor.smsgateway.data.repository.LoggerRepository
import com.questor.smsgateway.data.repository.SettingsRepository
import com.questor.smsgateway.server.dto.DeliveryAckRequest
import com.questor.smsgateway.server.dto.DeliveryAckResponse
import com.questor.smsgateway.server.dto.DeliveryBatchRequest
import com.questor.smsgateway.server.dto.DeliveryBatchResponse
import com.questor.smsgateway.server.dto.DeliveryStatusDto
import com.questor.smsgateway.server.dto.GatewayHealthResponse
import com.questor.smsgateway.server.dto.InboundAckRequest
import com.questor.smsgateway.server.dto.InboundAckResponse
import com.questor.smsgateway.server.dto.InboundBatchResponse
import com.questor.smsgateway.server.dto.InboundSmsDto
import com.questor.smsgateway.server.dto.SendSmsRequest
import com.questor.smsgateway.server.dto.SendSmsResponse
import com.questor.smsgateway.telephony.MultiSimManager
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface

class KtorHttpServer(
    private val context: Context,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository,
    private val simManager: MultiSimManager,
    private val webSocketHub: WebSocketHub,
    private val logger: LoggerRepository
) {
    private var serverEngine: ApplicationEngine? = null
    private val startTimeMillis = System.currentTimeMillis()
    @Volatile private var isRunning = false

    fun start(port: Int = 8765) {
        if (isRunning) return

        try {
            serverEngine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                configurePlugins()
                configureRoutes()
            }.start(wait = false)
            isRunning = true
            logger.i("KtorHttpServer", "Ktor HTTP & WebSocket server started on port $port")
        } catch (e: Exception) {
            logger.e("KtorHttpServer", "Failed to start Ktor server on port $port", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            isRunning = false
            logger.i("KtorHttpServer", "Ktor server stopped")
        } catch (e: Exception) {
            logger.w("KtorHttpServer", "Error stopping Ktor server", e)
        }
    }

    private fun Application.configurePlugins() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
                encodeDefaults = true
            })
        }

        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Questor-Gateway-Key")
            anyHost()
        }

        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 30_000
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
    }

    private fun Application.configureRoutes() {
        routing {
            get("/") {
                call.respondText(
                    "Questor SMS Gateway is running. Endpoints: /api/v1/gateway/health, /api/v1/messages, /api/v1/gateway/ws"
                )
            }

            // Health Endpoint
            get("/api/v1/gateway/health") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@get
                }

                val sims = simManager.getActiveSimCards()
                val primarySim = sims.firstOrNull()
                val (battPct, isCharging) = getBatteryInfo()
                val uptime = (System.currentTimeMillis() - startTimeMillis) / 1000

                val pending = gatewayRepo.pendingCountFlow.first()
                val sent = gatewayRepo.sentCountFlow.first()
                val delivered = gatewayRepo.deliveredCountFlow.first()
                val failed = gatewayRepo.failedCountFlow.first()
                val inbound = gatewayRepo.inboundCountFlow.first()
                val settings = settingsRepo.settingsFlow.first()

                call.respond(
                    GatewayHealthResponse(
                        isReady = true,
                        model = simManager.getDeviceModel(),
                        carrier = primarySim?.carrierName ?: "Ready",
                        signalStrength = primarySim?.signalDbm ?: "Level 4/4",
                        batteryPercent = battPct,
                        isCharging = isCharging,
                        serverUptimeSeconds = uptime,
                        pendingCount = pending,
                        sentCount = sent,
                        deliveredCount = delivered,
                        failedCount = failed,
                        inboundCount = inbound,
                        transport = settings.activeTransport.displayName
                    )
                )
            }

            // Send SMS Endpoint
            post("/api/v1/messages") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@post
                }

                try {
                    val req = call.receive<SendSmsRequest>()
                    if (req.messageId.isBlank() || req.to.isBlank() || req.message.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, SendSmsResponse(req.messageId, "error", "Invalid parameters"))
                        return@post
                    }

                    val entity = OutboxMessageEntity(
                        messageId = req.messageId,
                        toPhone = req.to,
                        messageText = req.message,
                        simSlot = req.simSlot,
                        status = "PENDING"
                    )

                    gatewayRepo.enqueueOutboxMessage(entity)
                    logger.i("KtorHttpServer", "Enqueued SMS ${req.messageId} to ${req.to}")
                    call.respond(HttpStatusCode.Accepted, SendSmsResponse(req.messageId, "accepted"))
                } catch (e: Exception) {
                    logger.e("KtorHttpServer", "Failed to process send request", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Server error")))
                }
            }

            // Single Delivery Query
            get("/api/v1/messages/{messageId}/delivery") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@get
                }

                val msgId = call.parameters["messageId"] ?: ""
                val report = gatewayRepo.getDeliveryReport(msgId)
                if (report != null) {
                    call.respond(
                        DeliveryStatusDto(
                            messageId = report.messageId,
                            status = report.status,
                            detail = report.detail,
                            updatedAt = report.updatedAtUtc.toString()
                        )
                    )
                } else {
                    val outbox = gatewayRepo.getOutboxMessage(msgId)
                    if (outbox != null) {
                        call.respond(
                            DeliveryStatusDto(
                                messageId = outbox.messageId,
                                status = outbox.status.lowercase(),
                                detail = outbox.errorMessage,
                                updatedAt = outbox.createdAtUtc.toString()
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Message not found"))
                    }
                }
            }

            // Batch Delivery Query
            post("/api/v1/messages/delivery/batch") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@post
                }

                val req = call.receive<DeliveryBatchRequest>()
                val reports = gatewayRepo.getDeliveryReportsBatch(req.messageIds)
                val results = reports.map {
                    DeliveryStatusDto(
                        messageId = it.messageId,
                        status = it.status,
                        detail = it.detail,
                        updatedAt = it.updatedAtUtc.toString()
                    )
                }
                call.respond(DeliveryBatchResponse(results))
            }

            // Batch Delivery Ack
            post("/api/v1/messages/delivery/ack") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@post
                }

                val req = call.receive<DeliveryAckRequest>()
                val acked = gatewayRepo.acknowledgeDeliveryReports(req.messageIds)
                call.respond(DeliveryAckResponse(acknowledgedCount = acked))
            }

            // Inbound SMS Query
            get("/api/v1/messages/inbound") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@get
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val inbounds = gatewayRepo.getUnacknowledgedInbound(limit)
                val list = inbounds.map {
                    InboundSmsDto(
                        id = it.id,
                        from = it.fromPhone,
                        message = it.messageText,
                        receivedAt = it.receivedAtUtc.toString(),
                        simSlot = it.simSlot
                    )
                }
                call.respond(InboundBatchResponse(list))
            }

            // Inbound SMS Ack
            post("/api/v1/messages/inbound/ack") {
                if (!validateAuth(call.request.header("X-Questor-Gateway-Key"))) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Invalid Gateway Key"))
                    return@post
                }

                val req = call.receive<InboundAckRequest>()
                gatewayRepo.acknowledgeInbound(req.messageIds)
                call.respond(InboundAckResponse(success = true, acknowledgedCount = req.messageIds.size))
            }

            // WebSocket Channel
            webSocket("/api/v1/gateway/ws") {
                val key = call.request.header("X-Questor-Gateway-Key") ?: call.request.queryParameters["key"]
                if (!validateAuth(key)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@webSocket
                }

                webSocketHub.addSession(this)
                logger.i("KtorHttpServer", "Questor WebSocket client connected")

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            if (text.contains("ping", ignoreCase = true)) {
                                send(Frame.Text("""{"event":"pong","timestamp":${System.currentTimeMillis()}}"""))
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.w("KtorHttpServer", "WebSocket session ended (${e.message})")
                } finally {
                    webSocketHub.removeSession(this)
                    logger.i("KtorHttpServer", "Questor WebSocket client disconnected")
                }
            }
        }
    }

    private suspend fun validateAuth(providedKey: String?): Boolean {
        val settings = settingsRepo.settingsFlow.first()
        if (settings.gatewayKey.isBlank()) return true // No key configured -> open access
        return settings.gatewayKey == providedKey
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val isCharging = batteryManager?.isCharging ?: false
        return Pair(level, isCharging)
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    // Check for tethering (rndis, usb) or wifi (wlan)
                    val addresses = intf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }
}
