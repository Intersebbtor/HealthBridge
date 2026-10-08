package com.healthbridge.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand palette, shared with the desktop app (see desktop Theme.kt). */
object Brand {
    val Teal = Color(0xFF2DD4BF)
    val Blue = Color(0xFF3B82F6)
    val Heart = Color(0xFFFB7185)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val gradient = Brush.linearGradient(listOf(Teal, Blue))
}

data class Surfaces(val card: Color, val border: Color, val track: Color, val muted: Color, val positive: Color, val warning: Color)

val LocalSurfaces = staticCompositionLocalOf { darkSurfaces }

private val darkSurfaces = Surfaces(
    card = Color(0xFF141C2B), border = Color(0xFF222D42), track = Color(0xFF1F2A3D), muted = Color(0xFF8A9BB4),
    positive = Brand.Green, warning = Brand.Amber
)
private val lightSurfaces = Surfaces(
    card = Color.White, border = Color(0xFFE3E8F0), track = Color(0xFFE8EDF4), muted = Color(0xFF64748B),
    positive = Color(0xFF059669), warning = Color(0xFFD97706)
)

private val darkScheme = darkColorScheme(
    primary = Brand.Teal, onPrimary = Color(0xFF042F2E),
    secondary = Brand.Blue, error = Brand.Heart,
    background = Color(0xFF0B1220), surface = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF141C2B), onSurfaceVariant = Color(0xFF8A9BB4)
)
private val lightScheme = lightColorScheme(
    primary = Color(0xFF0D9488), onPrimary = Color.White,
    secondary = Color(0xFF2563EB), error = Color(0xFFE11D48),
    background = Color(0xFFF4F7FB), surface = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F172A), onSurface = Color(0xFF0F172A),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun HealthBridgeTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSurfaces provides if (dark) darkSurfaces else lightSurfaces) {
        MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, content = content)
    }
}

@Composable
fun BridgeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalSurfaces.current
    Column(
        modifier = modifier
            .background(s.card, RoundedCornerShape(20.dp))
            .border(1.dp, s.border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        content = content
    )
}

/** Circular progress ring with the brand gradient. */
@Composable
fun StepRing(progress: Float, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
    val track = LocalSurfaces.current.track
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(listOf(Brand.Teal, Brand.Blue, Brand.Teal)),
                        0f, sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
            }
        }
        content()
    }
}

enum class LinkState { Connected, Stale, Waiting }

@Composable
fun StatusPill(state: LinkState, text: String) {
    val color = when (state) {
        LinkState.Connected -> LocalSurfaces.current.positive
        LinkState.Stale -> LocalSurfaces.current.warning
        LinkState.Waiting -> LocalSurfaces.current.muted
    }
    Row(
        Modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
