package com.questor.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.questor.smsgateway.GatewayApp
import com.questor.smsgateway.R
import com.questor.smsgateway.data.model.TransportMode
import com.questor.smsgateway.server.AoaUsbServer
import com.questor.smsgateway.server.BluetoothServer
import com.questor.smsgateway.server.KtorHttpServer
import com.questor.smsgateway.server.NsdDiscoveryBroadcaster
import com.questor.smsgateway.server.UdpDiscoveryServer
import com.questor.smsgateway.telephony.MultiSimManager
import com.questor.smsgateway.telephony.SmsDispatcher
import com.questor.smsgateway.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GatewayForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "questor_sms_gateway_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.questor.smsgateway.START_SERVICE"
        const val ACTION_STOP = "com.questor.smsgateway.STOP_SERVICE"
        const val ACTION_RELOAD = "com.questor.smsgateway.RELOAD_SETTINGS"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reloadService(context: Context) {
            if (!isServiceRunning) return
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_RELOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var statsJob: Job? = null

    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var ktorServer: KtorHttpServer
    private lateinit var nsdBroadcaster: NsdDiscoveryBroadcaster
    private lateinit var udpDiscoveryServer: UdpDiscoveryServer
    private lateinit var bluetoothServer: BluetoothServer
    private lateinit var aoaUsbServer: AoaUsbServer
    private lateinit var smsDispatcher: SmsDispatcher

    override fun onCreate() {
        super.onCreate()
        val app = application as GatewayApp

        wakeLockManager = WakeLockManager(this, app.loggerRepository)
        ktorServer = KtorHttpServer(
            this,
            app.gatewayRepository,
            app.settingsRepository,
            app.multiSimManager,
            app.webSocketHub,
            app.loggerRepository
        )
        nsdBroadcaster = NsdDiscoveryBroadcaster(this, app.loggerRepository)
        udpDiscoveryServer = UdpDiscoveryServer(
            this,
            app.settingsRepository,
            app.multiSimManager,
            app.loggerRepository
        )
        bluetoothServer = BluetoothServer(
            this,
            app.gatewayRepository,
            app.settingsRepository,
            app.multiSimManager,
            app.loggerRepository
        )
        aoaUsbServer = AoaUsbServer(
            this,
            app.gatewayRepository,
            app.settingsRepository,
            app.multiSimManager,
            app.loggerRepository
        )
        smsDispatcher = SmsDispatcher(
            this,
            app.gatewayRepository,
            app.settingsRepository,
            app.multiSimManager,
            app.loggerRepository
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (isServiceRunning) {
                    reloadActiveTransports()
                }
                return START_STICKY
            }
            else -> {
                startForegroundService()
                return START_STICKY
            }
        }
    }

    private fun startForegroundService() {
        if (isServiceRunning) return
        isServiceRunning = true

        val notification = buildNotification("Initializing Gateway…", "0 pending • 0 sent")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val app = application as GatewayApp
        serviceScope.launch {
            val settings = app.settingsRepository.settingsFlow.first()
            if (settings.acquireWakeLock) {
                wakeLockManager.acquire()
            }

            applyTransports(settings)

            // Start queue dispatcher
            smsDispatcher.start()
            app.loggerRepository.i("ForegroundService", "Questor SMS Gateway active (${settings.activeTransport.displayName})")

            observeStatsAndUpdateNotification()
        }
    }

    private fun reloadActiveTransports() {
        val app = application as GatewayApp
        serviceScope.launch {
            val settings = app.settingsRepository.settingsFlow.first()
            applyTransports(settings)
            app.loggerRepository.i("ForegroundService", "Reloaded transport listeners: ${settings.activeTransport.displayName}")
        }
    }

    private fun applyTransports(settings: com.questor.smsgateway.data.model.GatewaySettings) {
        when (settings.activeTransport) {
            TransportMode.USB_TETHER, TransportMode.WIFI_LAN -> {
                bluetoothServer.stop()
                aoaUsbServer.stop()
                ktorServer.start(settings.serverPort)
                nsdBroadcaster.start(settings.serverPort)
                udpDiscoveryServer.start(settings.serverPort)
            }
            TransportMode.BLUETOOTH -> {
                ktorServer.stop()
                nsdBroadcaster.stop()
                udpDiscoveryServer.stop()
                aoaUsbServer.stop()
                bluetoothServer.start()
            }
            TransportMode.USB_AOA -> {
                ktorServer.stop()
                nsdBroadcaster.stop()
                udpDiscoveryServer.stop()
                bluetoothServer.stop()
                aoaUsbServer.start()
            }
        }
    }

    private fun stopForegroundService() {
        isServiceRunning = false
        statsJob?.cancel()
        smsDispatcher.stop()
        ktorServer.stop()
        nsdBroadcaster.stop()
        udpDiscoveryServer.stop()
        bluetoothServer.stop()
        aoaUsbServer.stop()
        wakeLockManager.release()

        val app = application as GatewayApp
        app.loggerRepository.i("ForegroundService", "Questor SMS Gateway stopped")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeStatsAndUpdateNotification() {
        val app = application as GatewayApp
        statsJob = serviceScope.launch {
            combine(
                app.gatewayRepository.pendingCountFlow,
                app.gatewayRepository.sentCountFlow,
                app.gatewayRepository.failedCountFlow,
                app.settingsRepository.settingsFlow
            ) { pending, sent, failed, settings ->
                val ip = KtorHttpServer.getLocalIpAddress()
                val title = "Questor SMS Gateway — Connected (${settings.activeTransport.displayName})"
                val content = if (settings.activeTransport == TransportMode.BLUETOOTH) {
                    "Bluetooth Mode Active • $pending pending • $sent sent • $failed failed"
                } else {
                    "IP: $ip:${settings.serverPort} • $pending pending • $sent sent • $failed failed"
                }
                Pair(title, content)
            }.collect { (title, content) ->
                val notification = buildNotification(title, content)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, GatewayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop Service", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
