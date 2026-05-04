package com.healthbridge.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectManager = HealthConnectManager(this)
        discoveryManager = DiscoveryManager(this)

        val requestPermissionContract = PermissionController.createRequestPermissionResultContract()
        requestPermissionLauncher = registerForActivityResult(requestPermissionContract) { }

        setContent {
            HealthBridgeApp(healthConnectManager, discoveryManager, requestPermissionLauncher)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stopDiscovery()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthBridgeApp(
    healthConnectManager: HealthConnectManager,
    discoveryManager: DiscoveryManager,
    requestPermissionLauncher: ActivityResultLauncher<Set<String>>
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else { true }
        )
    }
    var sdkStatus by remember { mutableIntStateOf(HealthConnectClient.SDK_UNAVAILABLE) }
    var stepsToday by remember { mutableLongStateOf(0L) }
    var lastHeartRate by remember { mutableIntStateOf(0) }
    var serverIp by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var syncIntervalStr by remember { mutableStateOf("10") }
    var discoveredServer by remember { mutableStateOf<String?>(null) }
    var isDarkMode by remember { mutableStateOf(true) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationPermissionGranted = it }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            try {
                sdkStatus = healthConnectManager.checkAvailability()
                if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                    permissionsGranted = healthConnectManager.hasAllPermissions()
                    if (permissionsGranted) {
                        val now = Instant.now()
                        val zoneId = ZoneId.systemDefault()
                        val steps = healthConnectManager.readSteps(LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant(), now) ?: 0L
                        val heartRate = healthConnectManager.readHeartRate()
                        withContext(Dispatchers.Main) { stepsToday = steps; lastHeartRate = heartRate }
                    }
                }
                Unit
            } catch (e: Exception) { 
                Log.e("HealthBridge", "Refresh failed", e)
                Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        discoveryManager.startDiscovery { address, token ->
            discoveredServer = address
            if (serverIp.isEmpty()) { 
                serverIp = address
                apiToken = token 
            }
        }
        while(true) { refreshData(); delay(10000) }
    }

    LaunchedEffect(serverIp, apiToken, syncIntervalStr) {
        if (serverIp.isNotEmpty() && apiToken.isNotEmpty()) {
            val intent = Intent(context, SyncService::class.java).apply {
                putExtra("serverIp", serverIp)
                putExtra("apiToken", apiToken)
                putExtra("intervalSec", syncIntervalStr.toIntOrNull() ?: 10)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QRScanner { ip, token ->
                        serverIp = ip
                        apiToken = token
                        isScanning = false
                    }
                    IconButton(
                        onClick = { isScanning = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_shoe),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("HealthBridge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { isDarkMode = !isDarkMode }) {
                            Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Theme")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Health Connect Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Please install Health Connect to use this app.")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }) {
                                    Text("Install Health Connect")
                                }
                            }
                        }
                    } else if (!permissionsGranted) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Permissions Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("HealthBridge needs access to your health data to sync it with your desktop.")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { requestPermissionLauncher.launch(healthConnectManager.permissions) }) {
                                    Text("Grant Health Permissions")
                                }
                            }
                        }
                    } else {
                        // Metrics Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricCard("Steps Today", "$stepsToday", Icons.Default.DirectionsWalk, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            MetricCard("Heart Rate", if (lastHeartRate > 0) "$lastHeartRate bpm" else "--", Icons.Default.Favorite, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pairing Card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Desktop Pairing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (serverIp.isEmpty()) {
                                    Text("Not paired with any desktop server.", style = MaterialTheme.typography.bodyMedium)
                                    if (discoveredServer != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Found server at $discoveredServer", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { isScanning = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scan Pairing QR")
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("Paired with:", style = MaterialTheme.typography.labelMedium)
                                            Text(serverIp, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = { serverIp = ""; apiToken = "" }) {
                                            Text("Reset", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Interval Card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Frequency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Sync metrics every", style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = syncIntervalStr,
                                        onValueChange = { if (it.all { c -> c.isDigit() }) syncIntervalStr = it },
                                        modifier = Modifier.width(80.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Text("seconds", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Notifications Disabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Enabled notifications for better sync reliability.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                        Text("Enable")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

