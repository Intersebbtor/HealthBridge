package com.healthbridge.android

import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class SyncManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
        
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
suspend fun syncDataDetailed(serverIp: String, token: String, steps: Long, heartRate: Int): String {
    return withContext(Dispatchers.IO) {
        val cleanIp = serverIp.substringBefore(":")
        val url = "http://$cleanIp:8080/api/sync"
        Log.d("SyncManager", "Attempting sync to $url")

        val bodyJson = buildJsonObject {
            putJsonObject("metrics") {
                put("steps", steps)
                put("heart_rate", heartRate)
            }
        }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    Log.d("SyncManager", "Response code: ${response.code}")
                    when (response.code) {
                        200 -> "Sync Successful!"
                        401 -> "Sync Failed: Unauthorized (Token mismatch)"
                        404 -> "Sync Failed: Server endpoint not found"
                        else -> "Sync Failed: Server error ${response.code}"
                    }
                }
            } catch (e: ConnectException) {
                Log.e("SyncManager", "Connection refused: ${e.message}")
                "Sync Failed: Could not connect to server. Check IP/WiFi."
            } catch (e: SocketTimeoutException) {
                Log.e("SyncManager", "Connection timed out")
                "Sync Failed: Connection timed out."
            } catch (e: Exception) {
                Log.e("SyncManager", "Sync exception: ${e.message}", e)
                "Sync Failed: ${e.message ?: "Unknown error"}"
            }
        }
    }

    // Keep the old one for backward compatibility if needed, but we updated MainActivity
    suspend fun syncData(serverIp: String, token: String, steps: Long, heartRate: Int): Boolean {
        val result = syncDataDetailed(serverIp, token, steps, heartRate)
        return result == "Sync Successful!"
    }
}
