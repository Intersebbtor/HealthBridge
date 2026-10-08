import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.healthbridge.desktop.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.awt.Desktop
import java.net.URI
import java.time.Instant

fun main() {
    // Render the tray icon as a macOS template image (tinted for light/dark menu bars)
    System.setProperty("apple.awt.enableTemplateImages", "true")
    System.getenv("HEALTHBRIDGE_RENDER_PREVIEW")?.let { renderPreviews(it); return }

    application {
        val server = remember { HealthBridgeServer() }
        val receivedMetrics by server.receivedMetrics.collectAsState()
        val lastSyncAt by server.lastSyncAt.collectAsState()
        val now by produceState(Instant.now()) { while (true) { delay(1000); value = Instant.now() } }

        val systemDark = isSystemInDarkTheme()
        var isDarkMode by remember { mutableStateOf(systemDark) }
        var isWindowVisible by remember { mutableStateOf(true) }
        var showQR by remember { mutableStateOf(false) }
        var closeQrOnSync by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { server.start() }

        val localIp = remember { NetworkUtils.getLocalIpAddress() }
        val state = DashboardState(
            steps = receivedMetrics["steps"]?.jsonPrimitive?.longOrNull,
            heartRate = receivedMetrics["heart_rate"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
            lastSyncAt = lastSyncAt,
            now = now,
            address = "$localIp:${server.port}",
        )

        // Close the pairing dialog once a phone that was not connected before starts syncing
        LaunchedEffect(lastSyncAt) { if (showQR && closeQrOnSync && lastSyncAt != null) showQR = false }

        fun openPairing() {
            closeQrOnSync = state.link != LinkState.Connected
            showQR = true
            isWindowVisible = true
        }

        Tray(
            icon = painterResource("tray_template.svg"),
            tooltip = "HealthBridge",
            menu = {
                val steps = state.steps
                if (steps != null) {
                    val pct = (steps * 100 / DAILY_STEP_GOAL).toInt()
                    Item("${formatCount(steps)} steps  ·  $pct% of goal", onClick = { isWindowVisible = true })
                    Item("Heart rate: ${state.heartRate?.let { "$it bpm" } ?: "--"}", onClick = { isWindowVisible = true })
                    state.lastSyncAt?.let { Item("Synced ${relativeTime(it, now)}", enabled = false, onClick = {}) }
                } else {
                    Item("Waiting for phone…", enabled = false, onClick = {})
                }
                Separator()
                Item("Open Dashboard", onClick = { isWindowVisible = true })
                Item("Pair Phone…", onClick = { openPairing() })
                Separator()
                Item("Quit HealthBridge", onClick = { server.stop(); exitApplication() })
            }
        )

        if (isWindowVisible) {
            Window(
                onCloseRequest = { isWindowVisible = false },
                title = "HealthBridge",
                icon = painterResource("icons/icon.png"),
                state = rememberWindowState(size = DpSize(780.dp, 580.dp)),
            ) {
                // Let the content flow under a transparent macOS title bar
                LaunchedEffect(Unit) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
                HealthBridgeTheme(isDarkMode) {
                    Dashboard(
                        state = state,
                        dark = isDarkMode,
                        onToggleTheme = { isDarkMode = !isDarkMode },
                        onPair = { openPairing() },
                        onSupport = { runCatching { Desktop.getDesktop().browse(URI(SUPPORT_URL)) } },
                        topInset = 18.dp,
                    )
                    if (showQR) {
                        Dialog(onDismissRequest = { showQR = false }) {
                            val qr = remember(state.address) { generateQRCode("http://${state.address}/token=${server.apiToken}") }
                            PairCard(qr, state.address, onClose = { showQR = false })
                        }
                    }
                }
            }
        }
    }
}
