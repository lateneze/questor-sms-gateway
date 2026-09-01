package com.questor.smsgateway.server

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class AoaUsbServer(
    private val context: Context,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository,
    private val simManager: MultiSimManager,
    private val logger: LoggerRepository
) {
    private val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var workerJob: Job? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning || usbManager == null) return
        isRunning = true

        workerJob = scope.launch {
            logger.i("AoaUsbServer", "AOA USB Accessory monitor active")
            while (isActive && isRunning) {
                val accessoryList = usbManager.accessoryList
                if (!accessoryList.isNullOrEmpty()) {
                    val accessory = accessoryList[0]
                    handleAccessory(accessory)
                }
                kotlinx.coroutines.delay(2000L)
            }
        }
    }

    private suspend fun handleAccessory(accessory: UsbAccessory) {
        logger.i("AoaUsbServer", "USB Accessory attached: ${accessory.description} / ${accessory.manufacturer}")
        fileDescriptor = usbManager?.openAccessory(accessory) ?: return

        try {
            val inputStream = FileInputStream(fileDescriptor!!.fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor!!.fileDescriptor)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))

            while (scope.isActive && isRunning) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                val response = processCommand(line)
                writer.write(response)
                writer.newLine()
                writer.flush()
            }
        } catch (e: CancellationException) {
            // normal stop
        } catch (e: Exception) {
            logger.w("AoaUsbServer", "AOA Accessory stream disconnected (${e.message})")
        } finally {
            try { fileDescriptor?.close() } catch (_: Exception) {}
            fileDescriptor = null
        }
    }

    private suspend fun processCommand(line: String): String {
        return try {
            val req = json.decodeFromString<BtEnvelopeRequest>(line)
            val settings = settingsRepo.settingsFlow.first()

            if (settings.gatewayKey.isNotBlank() && req.key != settings.gatewayKey) {
                return json.encodeToString(
                    buildJsonObject {
                        put("id", req.id)
                        put("success", false)
                        put("error", "Unauthorized")
                    }
                )
            }

            when (req.action.uppercase()) {
                "GET_HEALTH", "HEALTH" -> {
                    val sims = simManager.getActiveSimCards()
                    val primarySim = sims.firstOrNull()
                    val pending = gatewayRepo.pendingCountFlow.first()
                    val sent = gatewayRepo.sentCountFlow.first()
                    val delivered = gatewayRepo.deliveredCountFlow.first()
                    val failed = gatewayRepo.failedCountFlow.first()
                    val inbound = gatewayRepo.inboundCountFlow.first()

                    val health = GatewayHealthResponse(
                        isReady = true,
                        model = simManager.getDeviceModel(),
                        carrier = primarySim?.carrierName ?: "Ready",
                        signalStrength = primarySim?.signalDbm ?: "Level 4/4",
                        pendingCount = pending,
                        sentCount = sent,
                        deliveredCount = delivered,
                        failedCount = failed,
                        inboundCount = inbound,
                        transport = "USB AOA"
                    )

                    json.encodeToString(
                        buildJsonObject {
                            put("id", req.id)
                            put("success", true)
                            put("data", json.encodeToString(health))
                        }
                    )
                }
                "SEND", "SEND_SMS", "MESSAGES.SEND", "SEND_MESSAGE" -> {
                    val payload = req.payload ?: error("Missing payload")
                    val messageId = payload["messageId"]?.toString()?.trim('"') ?: req.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    val toPhone = payload["to"]?.toString()?.trim('"')
                        ?: payload["toPhone"]?.toString()?.trim('"')
                        ?: payload["recipient"]?.toString()?.trim('"')
                        ?: error("Missing 'to' or 'toPhone' in payload")
                    val messageText = payload["message"]?.toString()?.trim('"')
                        ?: payload["messageText"]?.toString()?.trim('"')
                        ?: payload["text"]?.toString()?.trim('"')
                        ?: error("Missing 'message' or 'messageText' in payload")
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
                            put("id", req.id)
                            put("messageId", messageId)
                            put("success", true)
                            put("ok", true)
                            put("accepted", true)
                            put("data", "accepted")
                        }
                    )
                }
                "FETCH_INBOUND", "INBOUND.LIST", "INBOUND_LIST", "INBOUND" -> {
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
                            put("id", req.id)
                            put("success", true)
                            put("ok", true)
                            put("items", json.parseToJsonElement(rawListJson))
                            put("data", json.parseToJsonElement(rawListJson))
                        }
                    )
                }
                "ACK_INBOUND", "INBOUND.ACK", "ACK_INBOUND_LIST" -> {
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
                            put("id", req.id)
                            put("success", true)
                            put("ok", true)
                            put("data", messageIds.size)
                        }
                    )
                }
                "DELIVERY_BATCH", "DELIVERY.BATCH", "GET_DELIVERY_STATUS", "DELIVERY.GET" -> {
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
                            put("id", req.id)
                            put("success", true)
                            put("ok", true)
                            put("results", json.parseToJsonElement(rawReportsJson))
                            put("data", json.parseToJsonElement(rawReportsJson))
                        }
                    )
                }
                else -> {
                    json.encodeToString(
                        buildJsonObject {
                            put("id", req.id)
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
                    put("error", e.message ?: "Processing error")
                }
            )
        }
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
        try { fileDescriptor?.close() } catch (_: Exception) {}
        fileDescriptor = null
        logger.i("AoaUsbServer", "AOA USB server stopped")
    }
}
