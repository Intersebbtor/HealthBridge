package com.healthbridge.desktop

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().asSequence()
            
            // Try to find a non-loopback, up, multicast-capable WiFi or Ethernet interface
            val bestAddress = interfaces
                .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .firstOrNull()

            bestAddress?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    fun getMulticastCapableInterface(): java.net.InetAddress? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
