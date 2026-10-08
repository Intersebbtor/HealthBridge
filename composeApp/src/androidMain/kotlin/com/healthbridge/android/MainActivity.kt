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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
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
        enableEdgeToEdge()
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
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(systemDark) }

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

    HealthBridgeTheme(isDarkMode) {
        val surfaces = LocalSurfaces.current
        val muted = surfaces.muted
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    QRScanner { ip, token ->
                        serverIp = ip
                        apiToken = token
                        isScanning = false
                    }
                    Box(
                        Modifier.align(Alignment.Center).size(260.dp)
                            .border(3.dp, Brand.Teal, RoundedCornerShape(28.dp))
                    )
                    Column(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Scan the QR code", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Click \"Pair phone\" on your Mac to show it.", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = { isScanning = false },
                        modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("HealthBridge", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(4.dp))
                            when {
                                serverIp.isNotEmpty() -> StatusPill(LinkState.Connected, "Syncing to your Mac")
                                discoveredServer != null -> StatusPill(LinkState.Stale, "Mac found")
                                else -> StatusPill(LinkState.Waiting, "Not paired")
                            }
                        }
                        IconButton(onClick = { isDarkMode = !isDarkMode }) {
                            Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Theme", tint = muted)
                        }
                    }

                    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                        NoticeCard(
                            Icons.Default.HealthAndSafety, Brand.Heart, "Health Connect unavailable",
                            "Install Health Connect to let HealthBridge read your steps and heart rate.",
                            "Install Health Connect"
                        ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
                    } else if (!permissionsGranted) {
                        NoticeCard(
                            Icons.Default.Lock, Brand.Teal, "Permissions required",
                            "HealthBridge needs read access to steps and heart rate to show them on your Mac.",
                            "Grant permissions"
                        ) { requestPermissionLauncher.launch(healthConnectManager.permissions) }
                    } else {
                        val progress = stepsToday.toFloat() / DAILY_STEP_GOAL
                        val remaining = (DAILY_STEP_GOAL - stepsToday).coerceAtLeast(0)

                        // Steps hero
                        BridgeCard(Modifier.fillMaxWidth()) {
                            Text("Steps today", fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                StepRing(progress, 210.dp, 16.dp) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(formatCount(stepsToday), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("of ${formatCount(DAILY_STEP_GOAL)}", fontSize = 13.sp, color = muted)
                                    }
                                }
                            }
                            Text(
                                if (remaining == 0L) "Daily goal reached" else "${(progress * 100).toInt()}% of your daily goal",
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricTile(
                                Icons.Default.Favorite, Brand.Heart, "Heart rate",
                                if (lastHeartRate > 0) "$lastHeartRate" else "--", "bpm", Modifier.weight(1f)
                            )
                            MetricTile(
                                if (remaining == 0L) Icons.Default.EmojiEvents else Icons.Default.DirectionsWalk, Brand.Teal, "To go",
                                formatCount(remaining), "steps", Modifier.weight(1f)
                            )
                        }

                        // Pairing
                        BridgeCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Default.Laptop, Brand.Blue)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Your Mac", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        when {
                                            serverIp.isNotEmpty() -> "Paired with $serverIp"
                                            discoveredServer != null -> "Found on your network"
                                            else -> "Not paired yet"
                                        },
                                        fontSize = 13.sp, color = muted
                                    )
                                }
                                if (serverIp.isNotEmpty()) {
                                    TextButton(onClick = { serverIp = ""; apiToken = "" }) {
                                        Text("Unpair", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (serverIp.isEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { isScanning = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan pairing QR", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Sync interval
                        BridgeCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Default.Sync, Brand.Teal)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("Sync interval", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("How often your Mac gets fresh numbers", fontSize = 13.sp, color = muted)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("5", "10", "30", "60").forEach { sec ->
                                    FilterChip(
                                        selected = syncIntervalStr == sec,
                                        onClick = { syncIntervalStr = sec },
                                        label = { Text("$sec s", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                            labelColor = muted
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = surfaces.border,
                                            selectedBorderColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                            NoticeCard(
                                Icons.Default.NotificationsOff, Brand.Amber, "Notifications disabled",
                                "Allow notifications so background sync keeps running reliably.",
                                "Enable"
                            ) { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
                    }
                }
            }
        }
    }
}

const val DAILY_STEP_GOAL = 10_000L

fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(Brand.gradient),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.requiredSize(size * 1.45f)
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun MetricTile(icon: ImageVector, tint: Color, label: String, value: String, unit: String, modifier: Modifier) {
    val muted = LocalSurfaces.current.muted
    BridgeCard(modifier) {
        IconBadge(icon, tint)
        Spacer(Modifier.height(16.dp))
        Text(label, fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 13.sp, color = muted, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun NoticeCard(icon: ImageVector, tint: Color, title: String, text: String, action: String, onAction: () -> Unit) {
    BridgeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 14.sp, color = LocalSurfaces.current.muted, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}
