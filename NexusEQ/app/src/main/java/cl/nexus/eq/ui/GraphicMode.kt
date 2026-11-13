package cl.nexus.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nexus.eq.EqMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val BARS = 48

/** Frecuencia (20 Hz – 20 kHz, escala log) de la barra i. */
private fun freqAt(i: Int): Float = 20f * 1000f.pow(i / (BARS - 1f))

/** Cuánto se acentúa cada zona: 0 = ambos extremos, 1 = graves, 2 = agudos. */
private fun emphasisWeight(pos: Float, emphasis: Int): Float = when (emphasis) {
    1 -> 1f + 1.2f * (1f - pos)
    2 -> 1f + 1.2f * pos
    else -> 1f + 0.9f * abs(2f * pos - 1f)
}

/** Altura de la barra i: sigue la curva de EQ y se mueve en el tiempo. No escucha el audio. */
private fun ampAt(i: Int, t: Float, gains: FloatArray, centers: FloatArray, emphasis: Int): Float {
    val pos = i / (BARS - 1f)
    val db = EqMath.curveAt(gains, freqAt(i), centers)
    val base = ((db + 12f) / 32f).coerceIn(0.06f, 1f)                       // -12..+20 dB → 0..1
    val motion = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * (1.3f + 0.05f * i) + i * 0.9f))
    return (base * motion * emphasisWeight(pos, emphasis) * 0.8f).coerceIn(0.03f, 1f)
}

/** Rojo en graves → acento principal en medios → acento secundario en agudos. */
private fun freqColor(pos: Float): Color =
    if (pos < 0.5f) lerp(Neon.Red, Neon.Cyan, pos * 2f) else lerp(Neon.Cyan, Neon.Magenta, (pos - 0.5f) * 2f)

/**
 * Modo gráfico a pantalla completa (salvapantallas). Estilos: 0 barras, 1 onda, 2 radial.
 * Cualquier toque lo cierra.
 */
@Composable
fun GraphicModeOverlay(
    gains: FloatArray,
    centers: FloatArray,
    style: Int,
    emphasis: Int,
    title: String,
    onDismiss: () -> Unit,
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var base = 0L
        while (true) {
            withFrameNanos { now ->
                if (base == 0L) base = now
                time.floatValue = (now - base) / 1_000_000_000f
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val amps = FloatArray(BARS) { ampAt(it, t, gains, centers, emphasis) }
            when (style) {
                1 -> drawWave(amps, t)
                2 -> drawRadial(amps, t)
                else -> drawBars(amps)
            }
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Text(title, color = Neon.Chrome, fontFamily = Neon.Mono, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, letterSpacing = 2.sp)
            Text("ANIMACIÓN SEGÚN TU CURVA · TOCA PARA VOLVER", color = Neon.Dim,
                fontFamily = Neon.Mono, fontSize = 9.sp)
        }
    }
}

private fun DrawScope.drawBars(amps: FloatArray) {
    val w = size.width
    val h = size.height
    val slot = w / BARS
    val bw = slot * 0.62f
    val baseY = h * 0.78f
    val maxH = h * 0.62f
    val glow = 2.dp.toPx()
    for (i in 0 until BARS) {
        val bh = amps[i] * maxH
        val x = i * slot + (slot - bw) / 2f
        val c = freqColor(i / (BARS - 1f))
        drawRoundRect(c.copy(alpha = 0.25f), Offset(x - glow, baseY - bh - glow), Size(bw + 2 * glow, bh + 2 * glow), CornerRadius(bw / 2f))
        drawRoundRect(c, Offset(x, baseY - bh), Size(bw, bh), CornerRadius(bw / 2f))
        drawRoundRect(c.copy(alpha = 0.12f), Offset(x, baseY + 4.dp.toPx()), Size(bw, bh * 0.35f), CornerRadius(bw / 2f))   // reflejo
    }
    drawLine(Neon.Line, Offset(0f, baseY), Offset(w, baseY), strokeWidth = 1.dp.toPx())
}

private fun DrawScope.drawWave(amps: FloatArray, t: Float) {
    val w = size.width
    val h = size.height
    val cy = h * 0.5f
    val maxH = h * 0.34f
    val upper = Path()
    val lower = Path()
    val steps = 160
    for (k in 0..steps) {
        val x = w * k / steps
        val pos = k / steps.toFloat()
        val idx = pos * (BARS - 1)
        val i0 = idx.toInt().coerceIn(0, BARS - 2)
        val f = idx - i0
        val a = amps[i0] * (1f - f) + amps[i0 + 1] * f
        val y = a * maxH * (0.85f + 0.15f * sin(pos * 14f - t * 3f))
        if (k == 0) { upper.moveTo(x, cy - y); lower.moveTo(x, cy + y) }
        else { upper.lineTo(x, cy - y); lower.lineTo(x, cy + y) }
    }
    val brush = Brush.horizontalGradient(listOf(Neon.Red, Neon.Cyan, Neon.Magenta))
    drawLine(Neon.Line, Offset(0f, cy), Offset(w, cy), strokeWidth = 1.dp.toPx())
    drawPath(upper, brush, alpha = 0.22f, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
    drawPath(lower, brush, alpha = 0.22f, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
    drawPath(upper, brush, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(lower, brush, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawRadial(amps: FloatArray, t: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val m = min(size.width, size.height)
    val r0 = m * 0.2f
    val rMax = m * 0.28f
    val avg = amps.average().toFloat()
    drawCircle(Neon.Line, radius = r0 - 6.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
    drawCircle(freqColor(0.5f).copy(alpha = 0.12f + 0.15f * avg), radius = r0 * (0.9f + 0.12f * avg), center = Offset(cx, cy))
    val pi = PI.toFloat()
    for (i in 0 until BARS) {
        val ang = (i / BARS.toFloat()) * 2f * pi - pi / 2f + t * 0.15f
        val len = amps[i] * rMax
        val c = freqColor(i / (BARS - 1f))
        val s = Offset(cx + cos(ang) * r0, cy + sin(ang) * r0)
        val e = Offset(cx + cos(ang) * (r0 + len), cy + sin(ang) * (r0 + len))
        drawLine(c.copy(alpha = 0.25f), s, e, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
        drawLine(c, s, e, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}
