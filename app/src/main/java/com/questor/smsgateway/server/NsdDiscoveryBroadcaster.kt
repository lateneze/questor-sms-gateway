package com.questor.smsgateway.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.questor.smsgateway.data.repository.LoggerRepository

class NsdDiscoveryBroadcaster(
    private val context: Context,
    private val logger: LoggerRepository
) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var isRegistered = false

    fun start(port: Int = 8765) {
        if (isRegistered || nsdManager == null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "QuestorGateway_${android.os.Build.MODEL.replace(" ", "_")}"
            serviceType = "_questorgw._tcp."
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                isRegistered = true
                logger.i("NsdDiscovery", "mDNS service registered: ${info?.serviceName} on port $port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isRegistered = false
                logger.w("NsdDiscovery", "mDNS registration failed (error $errorCode)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                isRegistered = false
                logger.i("NsdDiscovery", "mDNS service unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isRegistered = false
                logger.w("NsdDiscovery", "mDNS unregistration failed (error $errorCode)")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            logger.e("NsdDiscovery", "Error registering NSD service", e)
        }
    }

    fun stop() {
        if (!isRegistered || nsdManager == null || registrationListener == null) return
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            logger.w("NsdDiscovery", "Error during NSD unregister", e)
        } finally {
            isRegistered = false
            registrationListener = null
        }
    }
}
