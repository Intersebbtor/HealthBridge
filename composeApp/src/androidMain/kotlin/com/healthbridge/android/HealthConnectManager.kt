package com.healthbridge.android

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun checkAvailability(): Int = HealthConnectClient.getSdkStatus(context)

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean = healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readSteps(startTime: Instant, endTime: Instant): Long? {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            
            // DIAGNOSTIC: Print EVERY source providing steps
            response.records.forEach { 
                Log.d("HealthConnect", "Source: ${it.metadata.dataOrigin.packageName}, Count: ${it.count}")
            }
            
            val totalSteps = response.records.sumOf { it.count }
            Log.d("HealthConnect", "Total Steps (Raw): $totalSteps")
            totalSteps
        } catch (e: Exception) {
            Log.e("HealthConnect", "Steps Error", e)
            0L
        }
    }

    suspend fun readHeartRate(): Int {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(1, ChronoUnit.DAYS)),
                    ascendingOrder = false,
                    pageSize = 5
                )
            )
            val latestValues = response.records.flatMap { it.samples }.map { it.beatsPerMinute }.take(5)
            latestValues.map { it.toInt() }.average().toInt()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun readSleepMinutes(startTime: Instant, endTime: Instant): Int = 0
}
