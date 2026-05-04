package com.healthbridge.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class SyncService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var syncManager: SyncManager
    
    private var syncIntervalSec: Int = 10
    private var serverIp: String = ""
    private var apiToken: String = ""
    
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        healthConnectManager = HealthConnectManager(this)
        syncManager = SyncManager()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SyncService", "Service starting...")
        serverIp = intent?.getStringExtra("serverIp") ?: ""
        apiToken = intent?.getStringExtra("apiToken") ?: ""
        syncIntervalSec = intent?.getIntExtra("intervalSec", 10) ?: 10
        
        val notification = createNotification("Background sync active (Every $syncIntervalSec sec)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        
        startSyncLoop()
        
        return START_NOT_STICKY
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    performSync()
                } catch (e: Exception) {
                    Log.e("SyncService", "Sync error: ${e.message}")
                }
                delay(syncIntervalSec * 1000L)
            }
        }
    }

    private suspend fun performSync() {
        if (serverIp.isEmpty() || apiToken.isEmpty()) return
        
        try {
            if (healthConnectManager.hasAllPermissions()) {
                val zoneId = ZoneId.systemDefault()
                val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
                val now = Instant.now()

                // FORCE FRESH READS: These queries bypass cached UI data and hit the DB directly
                val steps = healthConnectManager.readSteps(startOfToday, now) ?: 0L
                val heartRate = healthConnectManager.readHeartRate()

                Log.d("SyncService", "Performing background sync: Steps=$steps, HR=$heartRate")
                syncManager.syncDataDetailed(serverIp, apiToken, steps, heartRate)
            }
        } catch (e: Exception) {
            Log.e("SyncService", "PerformSync Error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sync_channel",
                "HealthBridge Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("HealthBridge")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
