package com.healthbridge.desktop

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

class HealthBridgeServer {
    private val _receivedMetrics = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val receivedMetrics: StateFlow<Map<String, JsonElement>> = _receivedMetrics

    private val _lastSyncAt = MutableStateFlow<Instant?>(null)
    val lastSyncAt: StateFlow<Instant?> = _lastSyncAt

    val apiToken = UUID.randomUUID().toString()
    private var jmdns: JmDNS? = null
    private var server: NettyApplicationEngine? = null

    val port = 8080

    fun start() {

        // Start mDNS advertising in a separate thread to not block or crash the main server
        Thread {
            try {
                val multicastAddr = NetworkUtils.getMulticastCapableInterface()
                if (multicastAddr != null) {
                    println("🔍 mDNS: Attempting to bind to ${multicastAddr.hostAddress}")
                    jmdns = JmDNS.create(multicastAddr)
                    val serviceInfo = ServiceInfo.create(
                        "_healthbridge._tcp.local.",
                        "HealthBridge-Desktop",
                        port,
                        "token=$apiToken"
                    )
                    jmdns?.registerService(serviceInfo)
                    println("🚀 mDNS: Advertising HealthBridge on ${multicastAddr.hostAddress}:$port")
                } else {
                    println("⚠️ mDNS: No multicast-capable interface found. Discovery may not work.")
                }
            } catch (e: Exception) {
                println("❌ mDNS: Error starting advertising (Non-fatal): ${e.message}")
            }
        }.start()

        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json()
            }
            routing {
                // ADDED: Simple check endpoint
                get("/") {
                    call.respondText("HealthBridge Server is running. Use POST /api/sync to send data.")
                }
                post("/api/sync") {
                    val authHeader = call.request.headers["Authorization"]
                    if (authHeader != "Bearer $apiToken") {
                        call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
                        return@post
                    }

                    val body = call.receive<JsonObject>()
                    val metrics = body["metrics"]?.jsonObject ?: emptyMap()
                    
                    _receivedMetrics.value = metrics
                    _lastSyncAt.value = Instant.now()
                    
                    call.respond(mapOf("status" to "success"))
                }
            }
        }
        server?.start(wait = false)
    }

    fun stop() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        server?.stop(1000, 2000)
    }
}
