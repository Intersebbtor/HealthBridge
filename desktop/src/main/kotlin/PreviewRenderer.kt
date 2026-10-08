package com.healthbridge.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.Instant

/**
 * Renders the dashboard with sample data to PNG files, without opening a window.
 * Used for README screenshots and visual checks:
 *   HEALTHBRIDGE_RENDER_PREVIEW=/some/dir ./gradlew :desktop:run
 */
@OptIn(ExperimentalComposeUiApi::class)
fun renderPreviews(dir: String) {
    val out = File(dir).apply { mkdirs() }
    val now = Instant.now()
    val synced = DashboardState(6_240, 72, now.minusSeconds(8), now, "192.168.1.20:8080")
    val waiting = DashboardState(null, null, null, now, "192.168.1.20:8080")

    fun render(name: String, w: Int = 780, h: Int = 562, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w * 2, h * 2, Density(2f)) { content() }
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        File(out, "$name.png").writeBytes(bytes)
        println("Rendered ${out.resolve("$name.png")}")
    }

    for (dark in listOf(true, false)) {
        val suffix = if (dark) "dark" else "light"
        render("dashboard-$suffix") { HealthBridgeTheme(dark) { Dashboard(synced, dark, {}, {}, {}) } }
        render("waiting-$suffix") { HealthBridgeTheme(dark) { Dashboard(waiting, dark, {}, {}, {}) } }
    }
    render("tray-icon", 44, 22) {
        Box(Modifier.fillMaxSize().background(Color(0xFFECECEC)), contentAlignment = Alignment.Center) {
            Image(painterResource("tray_template.svg"), null, Modifier.size(18.dp))
        }
    }
    render("pair-dark", 420, 540) {
        HealthBridgeTheme(true) {
            Box(Modifier.fillMaxSize().background(Color(0xFF05080F)), contentAlignment = Alignment.Center) {
                PairCard(generateQRCode("http://192.168.1.20:8080/token=preview"), "192.168.1.20:8080") {}
            }
        }
    }
}
