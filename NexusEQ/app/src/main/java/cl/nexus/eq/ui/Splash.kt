package cl.nexus.eq.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pantalla de inicio: un ecualizador que sube y baja, con el título en glitch.
 * Dura ~1,6 s y se puede saltar tocando.
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1600, easing = LinearEasing))
        onDone()
    }
    val infinite = rememberInfiniteTransition(label = "splash")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val p = progress.value
    val fade = if (p > 0.82f) ((1f - p) / 0.18f).coerceIn(0f, 1f) else 1f
    val reveal = (p / 0.35f).coerceIn(0f, 1f)          // las barras suben al inicio
    val jitter = (sin(phase * 3f) * 2.5f).dp            // temblor del título

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade)
            .background(Neon.Bg)
            .pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 260.dp, height = 110.dp)) {
                val n = 24
                val slot = size.width / n
                val bw = slot * 0.6f
                for (i in 0 until n) {
                    val wave = 0.5f + 0.5f * sin(phase - i * 0.5f)
                    val env = 0.35f + 0.65f * sin(i / (n - 1f) * PI.toFloat())   // más alto al centro
                    val h = size.height * (0.12f + 0.88f * wave * env) * reveal
                    val c = lerp(Neon.Red, Neon.Cyan, i / (n - 1f))
                    val x = i * slot + (slot - bw) / 2f
                    drawRoundRect(c.copy(alpha = 0.25f), Offset(x - 2f, size.height - h - 2f), Size(bw + 4f, h + 4f), CornerRadius(bw / 2f))
                    drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Box {
                Text("NEXUS.EQ", color = Neon.Magenta.copy(alpha = 0.85f), fontFamily = Neon.Mono,
                    fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 4.sp,
                    modifier = Modifier.offset(x = -jitter))
                Text("NEXUS.EQ", color = Neon.Cyan.copy(alpha = 0.85f), fontFamily = Neon.Mono,
                    fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 4.sp,
                    modifier = Modifier.offset(x = jitter))
                Text("NEXUS.EQ", color = Neon.Chrome, fontFamily = Neon.Mono,
                    fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 4.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("DSP · 8 / 10 / 16 / 32 BANDAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 11.sp)
        }
    }
}
