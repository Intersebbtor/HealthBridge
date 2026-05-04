import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Tray
import androidx.compose.ui.res.painterResource
import com.healthbridge.desktop.HealthBridgeServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.jetbrains.skia.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    val server = remember { HealthBridgeServer() }
    val receivedMetrics by server.receivedMetrics.collectAsState()
    val lastSync by server.lastSync.collectAsState()
    
    var showQR by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(true) }
    var isWindowVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { server.start() }

    val steps = receivedMetrics["steps"]?.toString() ?: "0"
    val heartRate = (receivedMetrics["heart_rate"]?.toString() ?: "0") + " bpm"

    Tray(
        icon = painterResource("icon_16x16.png"),
        tooltip = "HealthBridge",
        menu = {
            Item("Steps: $steps", onClick = {})
            Item("Heart Rate: $heartRate", onClick = {})
            Separator()
            Item("Show Dashboard", onClick = { isWindowVisible = true })
            Item("Quit", onClick = { server.stop(); exitApplication() })
        }
    )

    if (isWindowVisible) {
        Window(
            onCloseRequest = { isWindowVisible = false },
            title = "HealthBridge Dashboard"
        ) {
            MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource("icon_16x16.png"),
                                        contentDescription = "Logo",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Text("HealthBridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isDarkMode = !isDarkMode }) {
                                    Icon(if (isDarkMode) Icons.Default.Face else Icons.Default.Build, contentDescription = "Toggle Theme")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { showQR = true }) {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pair Device")
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        if (lastSync == null) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Waiting for connection...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Last sync: $lastSync", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Spacer(Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    MetricCard("Steps", steps, Icons.Default.Info, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                    MetricCard("Heart Rate", heartRate, Icons.Default.Favorite, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Divider()
                        Spacer(Modifier.height(20.dp))
                        Text("Server Status: Running on port 8080", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    if (showQR && receivedMetrics.isNotEmpty()) {
                        showQR = false
                    }
                    if (showQR) {
                        Dialog(onDismissRequest = { showQR = false }) {
                            Surface(modifier = Modifier.padding(16.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                    val localIp = remember { com.healthbridge.desktop.NetworkUtils.getLocalIpAddress() }
                                    val qrBitmap = remember { generateQRCode("http://$localIp:8080/token=${server.apiToken}") }
                                    qrBitmap?.let {
                                        Image(
                                            bitmap = it.asComposeImageBitmap(),
                                            contentDescription = "Pairing QR Code",
                                            modifier = Modifier.size(200.dp).background(Color.White).padding(8.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Server IP: $localIp", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Scan this with the HealthBridge app.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { showQR = false }) { Text("Close") }
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
fun MetricCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = ByteArray(width * height * 4)
        var k = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                pixels[k++] = (color shr 16 and 0xFF).toByte()
                pixels[k++] = (color shr 8 and 0xFF).toByte()
                pixels[k++] = (color and 0xFF).toByte()
                pixels[k++] = (color shr 24 and 0xFF).toByte()
            }
        }
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        bitmap.installPixels(pixels)
        bitmap
    } catch (e: Exception) {
        null
    }
}
