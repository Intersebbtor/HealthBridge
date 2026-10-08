package com.healthbridge.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant

const val DAILY_STEP_GOAL = 10_000L

/** Optional donation link. The button is hidden while this is empty. */
const val SUPPORT_URL = ""

data class DashboardState(
    val steps: Long?,
    val heartRate: Int?,
    val lastSyncAt: Instant?,
    val now: Instant,
    val address: String,
) {
    val link: LinkState
        get() = when {
            lastSyncAt == null -> LinkState.Waiting
            Duration.between(lastSyncAt, now).seconds <= 60 -> LinkState.Connected
            else -> LinkState.Stale
        }

    val statusText: String
        get() = when (link) {
            LinkState.Waiting -> "Waiting for phone"
            LinkState.Connected -> "Connected"
            LinkState.Stale -> "Last seen ${relativeTime(lastSyncAt!!, now)}"
        }
}

fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

fun relativeTime(then: Instant, now: Instant): String {
    val s = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        s < 5 -> "just now"
        s < 60 -> "$s s ago"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
}

@Composable
fun Dashboard(
    state: DashboardState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onPair: () -> Unit,
    onSupport: () -> Unit,
    topInset: Dp = 0.dp,
) {
    val muted = LocalSurfaces.current.muted
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(start = 28.dp, end = 28.dp, top = topInset + 20.dp, bottom = 18.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource("logo.png"), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("HealthBridge", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                StatusPill(state.link, state.statusText)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleTheme) {
                Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme", tint = muted)
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onPair, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pair phone", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(22.dp))

        if (state.lastSyncAt == null || state.steps == null) {
            WaitingCard(onPair, Modifier.weight(1f).fillMaxWidth())
        } else {
            val steps = state.steps
            val progress = steps.toFloat() / DAILY_STEP_GOAL
            val remaining = (DAILY_STEP_GOAL - steps).coerceAtLeast(0)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BridgeCard(Modifier.weight(1.25f).fillMaxHeight()) {
                    Text("Steps today", fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StepRing(progress, 210.dp, 16.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatCount(steps), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
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
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricTile(
                        Icons.Default.Favorite, Brand.Heart, "Heart rate",
                        state.heartRate?.toString() ?: "--", "bpm", "Average of the latest readings",
                        Modifier.weight(1f).fillMaxWidth()
                    )
                    MetricTile(
                        if (remaining == 0L) Icons.Default.EmojiEvents else Icons.Default.DirectionsWalk, Brand.Teal, "To go",
                        formatCount(remaining), "steps",
                        if (remaining == 0L) "Nice work, goal reached" else "Until ${formatCount(DAILY_STEP_GOAL)} steps",
                        Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Footer
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, null, Modifier.size(14.dp), tint = muted)
            Spacer(Modifier.width(6.dp))
            Text("Listening on ${state.address}", fontSize = 12.sp, color = muted)
            state.lastSyncAt?.let {
                Text("  ·  Last sync ${relativeTime(it, state.now)}", fontSize = 12.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            if (SUPPORT_URL.isNotEmpty()) {
                TextButton(onClick = onSupport) {
                    Icon(Icons.Default.LocalCafe, null, Modifier.size(16.dp), tint = muted)
                    Spacer(Modifier.width(6.dp))
                    Text("Support", fontSize = 12.sp, color = muted)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector, tint: Color, label: String, value: String, unit: String, caption: String, modifier: Modifier
) {
    val muted = LocalSurfaces.current.muted
    BridgeCard(modifier) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
        Spacer(Modifier.weight(1f))
        Text(label, fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(5.dp))
            Text(unit, fontSize = 14.sp, color = muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(caption, fontSize = 12.sp, color = muted)
    }
}

@Composable
private fun WaitingCard(onPair: () -> Unit, modifier: Modifier) {
    val muted = LocalSurfaces.current.muted
    BridgeCard(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource("logo.png"), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(18.dp))
            Text("Waiting for your phone", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Open HealthBridge on your Android phone.\nIt finds this Mac on your Wi-Fi automatically.",
                fontSize = 14.sp, color = muted, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onPair, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pair with QR code")
            }
        }
    }
}

@Composable
fun PairCard(qr: ImageBitmap?, address: String, onClose: () -> Unit) {
    val muted = LocalSurfaces.current.muted
    BridgeCard(Modifier.width(340.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pair your phone", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scan this code with the HealthBridge app\non your Android phone.",
                fontSize = 13.sp, color = muted, textAlign = TextAlign.Center, lineHeight = 18.sp
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                if (qr != null) Image(qr, "Pairing QR code", Modifier.size(210.dp))
                else Box(Modifier.size(210.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.background(LocalSurfaces.current.track, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Laptop, null, Modifier.size(14.dp), tint = muted)
                Spacer(Modifier.width(6.dp))
                Text(address, fontSize = 12.sp, color = muted)
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onClose, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Done") }
        }
    }
}
