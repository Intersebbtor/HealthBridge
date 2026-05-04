package com.healthbridge.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class DiscoveryManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_healthbridge._tcp."
    
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onServiceFound: (String, String) -> Unit) {
        stopDiscovery()
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("DiscoveryManager", "Discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("DiscoveryManager", "Stop discovery failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d("DiscoveryManager", "Discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d("DiscoveryManager", "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d("DiscoveryManager", "Service found: ${serviceInfo?.serviceName}")
                if (serviceInfo?.serviceType?.contains("healthbridge") == true) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.e("DiscoveryManager", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                            Log.d("DiscoveryManager", "Service resolved: ${resolvedServiceInfo?.host}")
                            val host = resolvedServiceInfo?.host?.hostAddress
                            val port = resolvedServiceInfo?.port
                            
                            // Get token from TXT record if possible
                            // Note: NsdServiceInfo.getAttributes() is available since API 21
                            val attributes = resolvedServiceInfo?.attributes
                            val token = attributes?.get("token")?.let { String(it) } ?: ""
                            
                            if (host != null) {
                                onServiceFound("$host:$port", token)
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d("DiscoveryManager", "Service lost: ${serviceInfo?.serviceName}")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("DiscoveryManager", "Error stopping discovery", e)
            }
            discoveryListener = null
        }
    }
}
