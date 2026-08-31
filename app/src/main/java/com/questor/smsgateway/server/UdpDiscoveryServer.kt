package com.questor.smsgateway.server

import android.content.Context
import android.os.Build
import com.questor.smsgateway.data.repository.LoggerRepository
import com.questor.smsgateway.data.repository.SettingsRepository
import com.questor.smsgateway.telephony.MultiSimManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscoveryServer(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val simManager: MultiSimManager,
    private val logger: LoggerRepository
) {
    companion object {
        const val DISCOVERY_PORT = 8766
        const val MAGIC_REQUEST = "QUESTOR_DISCOVER"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var socket: DatagramSocket? = null
    @Volatile private var isRunning = false

    fun start(httpPort: Int = 8765) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch {
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket?.broadcast = true
                logger.i("UdpDiscovery", "UDP discovery server listening on port $DISCOVERY_PORT")

                val buffer = ByteArray(1024)

                while (isActive && isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket?.receive(packet) ?: break
                    } catch (e: Exception) {
                        if (!isRunning) break
                        continue
                    }

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (message.contains(MAGIC_REQUEST, ignoreCase = true)) {
                        val settings = settingsRepo.settingsFlow.first()
                        val sims = simManager.getActiveSimCards()
                        val primarySim = sims.firstOrNull()
                        val localIp = KtorHttpServer.getLocalIpAddress(settings.activeTransport)

                        val responseJson = buildJsonObject {
                            put("service", "QuestorGateway")
                            put("ip", localIp)
                            put("port", settings.serverPort)
                            put("baseUrl", "http://$localIp:${settings.serverPort}")
                            put("model", simManager.getDeviceModel())
                            put("carrier", primarySim?.carrierName ?: "Ready")
                            put("signalStrength", primarySim?.signalDbm ?: "Level 4/4")
                            put("transport", settings.activeTransport.displayName)
                        }.toString()

                        val sendData = responseJson.toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(
                            sendData,
                            sendData.size,
                            packet.address,
                            packet.port
                        )
                        socket?.send(replyPacket)
                        logger.i("UdpDiscovery", "Responded to discovery probe from ${packet.address.hostAddress}:${packet.port}")
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                logger.e("UdpDiscovery", "UDP discovery server error", e)
            } finally {
                closeSocket()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        closeSocket()
        logger.i("UdpDiscovery", "UDP discovery server stopped")
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
