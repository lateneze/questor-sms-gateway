package com.questor.smsgateway.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import com.questor.smsgateway.data.repository.GatewayRepository
import com.questor.smsgateway.data.repository.LoggerRepository
import com.questor.smsgateway.data.repository.SettingsRepository
import com.questor.smsgateway.server.dto.DeliveryBatchRequest
import com.questor.smsgateway.server.dto.GatewayHealthResponse
import com.questor.smsgateway.server.dto.InboundAckRequest
import com.questor.smsgateway.server.dto.InboundSmsDto
import com.questor.smsgateway.server.dto.SendSmsRequest
import com.questor.smsgateway.telephony.MultiSimManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

@Serializable
data class BtEnvelopeRequest(
    val id: String = "",
    val requestId: String = "",
    val action: String = "",
    val key: String? = null,
    val headers: Map<String, String>? = null,
    val payload: JsonObject? = null
)

class BluetoothServer(
    private val context: Context,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository,
    private val simManager: MultiSimManager,
    private val logger: LoggerRepository
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7b5e1ad3-5ce2-4e52-9f5b-fba319d2d6b0")
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val SERVICE_NAME = "QuestorSMSBridge"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            logger.w("BluetoothServer", "Bluetooth not enabled or not supported")
            return
        }

        isRunning = true
        serverJob = scope.launch {
            try {
                // Prioritize standard Serial Port Profile (SPP) with insecure RFCOMM for maximum Windows compatibility
                serverSocket = try {
                    bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                } catch (e: Exception) {
                    logger.w("BluetoothServer", "Insecure SPP RFCOMM failed, trying secure SPP: ${e.message}")
                    try {
                        bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                    } catch (e2: Exception) {
                        logger.w("BluetoothServer", "SPP failed, trying custom SERVICE_UUID: ${e2.message}")
                        bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    }
                }

                logger.i("BluetoothServer", "Bluetooth RFCOMM listening for connections (SPP/RFCOMM ready)")

                while (isActive && isRunning) {
                    val socket: BluetoothSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (!isRunning) break
                        logger.w("BluetoothServer", "Error accepting BT socket: ${e.message}")
                        break
                    }

                    handleClientSocket(socket)
                }
            } catch (e: CancellationException) {
                // Stopped normally
            } catch (e: Exception) {
                logger.e("BluetoothServer", "Fatal error in BT server socket", e)
            } finally {
                closeSocket()
            }
        }
    }

    private fun handleClientSocket(socket: BluetoothSocket) {
        scope.launch {
            logger.i("BluetoothServer", "Connected to Bluetooth client: ${socket.remoteDevice?.name ?: socket.remoteDevice?.address}")
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

                while (isActive && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val responseLine = processCommand(line)
                    writer.write(responseLine)
                    writer.newLine()
                    writer.flush()
                }
            } catch (e: Exception) {
                logger.w("BluetoothServer", "Bluetooth client disconnected (${e.message})")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun processCommand(line: String): String {
        return try {
            val req = json.decodeFromString<BtEnvelopeRequest>(line)
            val effectiveId = if (req.id.isNotBlank()) req.id else req.requestId
            val settings = settingsRepo.settingsFlow.first()

            val providedKey = req.key ?: req.headers?.get("X-Gateway-Key") ?: req.headers?.get("X-Questor-Gateway-Key")
            if (settings.gatewayKey.isNotBlank() && providedKey != settings.gatewayKey) {
                return json.encodeToString(
                    buildJsonObject {
                        put("id", effectiveId)
                        put("requestId", effectiveId)
                        put("success", false)
                        put("ok", false)
                        put("error", "Unauthorized")
                    }
                )
            }

            val normalizedAction = req.action.trim().uppercase()
            when {
                normalizedAction in listOf("GET_HEALTH", "HEALTH", "PROBE", "GATEWAY.HEALTH") -> {
                    val sims = simManager.getActiveSimCards()
                    val primarySim = sims.firstOrNull()
                    val pending = gatewayRepo.pendingCountFlow.first()
                    val sent = gatewayRepo.sentCountFlow.first()
                    val delivered = gatewayRepo.deliveredCountFlow.first()
                    val failed = gatewayRepo.failedCountFlow.first()
                    val inbound = gatewayRepo.inboundCountFlow.first()
                    val model = simManager.getDeviceModel()
                    val carrier = primarySim?.carrierName ?: "Ready"
                    val signal = primarySim?.signalDbm ?: "Level 4/4"

                    val health = GatewayHealthResponse(
                        isReady = true,
                        model = model,
                        carrier = carrier,
                        signalStrength = signal,
                        pendingCount = pending,
                        sentCount = sent,
                        deliveredCount = delivered,
                        failedCount = failed,
                        inboundCount = inbound,
                        transport = "Bluetooth"
                    )

                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", true)
                            put("ok", true)
                            put("ready", true)
                            put("isReady", true)
                            put("model", model)
                            put("carrier", carrier)
                            put("networkOperator", carrier)
                            put("signalStrength", signal)
                            put("transport", "Bluetooth")
                            put("data", json.encodeToString(health))
                        }
                    )
                }
                normalizedAction in listOf("SEND", "SEND_SMS", "MESSAGES.SEND", "SEND_MESSAGE") -> {
                    val payload = req.payload ?: error("Missing payload")
                    val messageId = payload["messageId"]?.toString()?.trim('"') ?: effectiveId.ifBlank { UUID.randomUUID().toString() }
                    val toPhone = payload["to"]?.toString()?.trim('"')
                        ?: payload["toPhone"]?.toString()?.trim('"')
                        ?: payload["recipient"]?.toString()?.trim('"')
                        ?: error("Missing 'to' or 'toPhone' field in payload")
                    val messageText = payload["message"]?.toString()?.trim('"')
                        ?: payload["messageText"]?.toString()?.trim('"')
                        ?: payload["text"]?.toString()?.trim('"')
                        ?: error("Missing 'message' or 'messageText' field in payload")
                    val simSlot = payload["simSlot"]?.toString()?.toIntOrNull()

                    val entity = OutboxMessageEntity(
                        messageId = messageId,
                        toPhone = toPhone,
                        messageText = messageText,
                        simSlot = simSlot,
                        status = "PENDING"
                    )
                    gatewayRepo.enqueueOutboxMessage(entity)
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("messageId", messageId)
                            put("success", true)
                            put("ok", true)
                            put("accepted", true)
                            put("data", "accepted")
                        }
                    )
                }
                normalizedAction in listOf("FETCH_INBOUND", "INBOUND.LIST", "INBOUND_LIST", "INBOUND") -> {
                    val inbounds = gatewayRepo.getUnacknowledgedInbound(100)
                    val list = inbounds.map {
                        InboundSmsDto(
                            id = it.id,
                            from = it.fromPhone,
                            message = it.messageText,
                            receivedAt = it.receivedAtUtc.toString(),
                            simSlot = it.simSlot
                        )
                    }
                    val rawListJson = json.encodeToString(list)
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", true)
                            put("ok", true)
                            put("items", json.parseToJsonElement(rawListJson))
                            put("data", json.parseToJsonElement(rawListJson))
                        }
                    )
                }
                normalizedAction in listOf("ACK_INBOUND", "INBOUND.ACK", "ACK_INBOUND_LIST") -> {
                    val payload = req.payload ?: error("Missing payload")
                    val idsJson = payload["messageIds"]
                    val messageIds = if (idsJson != null) {
                        json.decodeFromJsonElement<List<String>>(idsJson)
                    } else emptyList()

                    if (messageIds.isNotEmpty()) {
                        gatewayRepo.acknowledgeInbound(messageIds)
                    }
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", true)
                            put("ok", true)
                            put("data", messageIds.size)
                        }
                    )
                }
                normalizedAction in listOf("DELIVERY_BATCH", "DELIVERY.BATCH", "GET_DELIVERY_STATUS", "DELIVERY.GET") -> {
                    val payload = req.payload ?: error("Missing payload")
                    val idsJson = payload["messageIds"]
                    val messageIds = if (idsJson != null) {
                        json.decodeFromJsonElement<List<String>>(idsJson)
                    } else {
                        val singleId = payload["messageId"]?.toString()?.trim('"')
                        if (!singleId.isNullOrBlank()) listOf(singleId) else emptyList()
                    }
                    val reports = gatewayRepo.getDeliveryReportsBatch(messageIds)
                    val rawReportsJson = json.encodeToString(reports)
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", true)
                            put("ok", true)
                            put("results", json.parseToJsonElement(rawReportsJson))
                            put("data", json.parseToJsonElement(rawReportsJson))
                        }
                    )
                }
                normalizedAction in listOf("ACK_DELIVERY", "DELIVERY.ACK") -> {
                    val payload = req.payload ?: error("Missing payload")
                    val idsJson = payload["messageIds"]
                    val messageIds = if (idsJson != null) {
                        json.decodeFromJsonElement<List<String>>(idsJson)
                    } else emptyList()

                    if (messageIds.isNotEmpty()) {
                        gatewayRepo.acknowledgeDeliveryReports(messageIds)
                    }
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", true)
                            put("ok", true)
                            put("data", messageIds.size)
                        }
                    )
                }
                else -> {
                    json.encodeToString(
                        buildJsonObject {
                            put("id", effectiveId)
                            put("requestId", effectiveId)
                            put("success", false)
                            put("ok", false)
                            put("error", "Unknown action: ${req.action}")
                        }
                    )
                }
            }
        } catch (e: Exception) {
            json.encodeToString(
                buildJsonObject {
                    put("success", false)
                    put("ok", false)
                    put("error", e.message ?: "Processing error")
                }
            )
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        closeSocket()
        logger.i("BluetoothServer", "Bluetooth server stopped")
    }

    private fun closeSocket() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
