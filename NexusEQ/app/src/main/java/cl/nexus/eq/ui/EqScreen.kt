package cl.nexus.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.nexus.eq.Bands
import cl.nexus.eq.EqMath
import cl.nexus.eq.EqRepository
import cl.nexus.eq.Presets
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun EqScreen() {
    val app by EqRepository.state.collectAsStateWithLifecycle()
    val sessions by EqRepository.sessions.collectAsStateWithLifecycle()
    val globalUnsupported by EqRepository.globalUnsupported.collectAsStateWithLifecycle()
    val eq = app.current
    val gains = EqMath.effectiveGains(eq)
    val preamp = EqMath.effectivePreamp(eq)
    val hooked = sessions.count { it != 0 }

    Column(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /* ── Header ── */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEXUS.EQ", color = Neon.Cyan, fontFamily = Neon.Mono,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 3.sp)
                Text("DSP · SPOTIFY · 10 BANDAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            }
            PowerButton(app.enabled) { EqRepository.update { it.copy(enabled = !it.enabled) } }
        }

        /* ── Telemetría ── */
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("SALIDA", app.route.label, Neon.Cyan, Modifier.weight(1f))
            StatTile(
                "ENLACE",
                if (hooked > 0) "$hooked SES." else "ESPERA",
                if (hooked > 0) Neon.Lime else Neon.Amber,
                Modifier.weight(1f),
            )
            StatTile("PREAMP", fmtDb(preamp), if (preamp > 0) Neon.Red else Neon.Text, Modifier.weight(1f))
            StatTile("LIMIT", if (eq.limiter) "ON" else "OFF",
                if (eq.limiter) Neon.Lime else Neon.Dim, Modifier.weight(1f))
        }

        /* ── Curva de respuesta ── */
        Panel {
            Text("RESPUESTA · ${eq.presetName}", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            ResponseCurve(gains, active = app.enabled, modifier = Modifier.fillMaxWidth().height(110.dp))
        }

        /* ── Faders ── */
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until Bands.COUNT) {
                    BandFader(
                        label = Bands.LABELS[i],
                        value = eq.gains[i],
                        onChange = { EqRepository.setBand(i, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("Doble toque en una banda = 0 dB", color = Neon.Dim, fontFamily = Neon.Mono,
                fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        }

        /* ── Presets ── */
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.ALL.forEach { p ->
                Chip(p.name, selected = eq.presetName == p.name) { EqRepository.applyPreset(p) }
            }
        }

        /* ── Preamp / Bass ── */
        Panel {
            LabeledSlider("PREAMP", fmtDb(eq.preampDb), eq.preampDb, -12f..6f, Neon.Cyan) { v ->
                EqRepository.updateCurrent { it.copy(preampDb = (v * 2).roundToInt() / 2f) }
            }
            LabeledSlider("BASS BOOST", "${(eq.bassBoost * 100).roundToInt()}%", eq.bassBoost, 0f..1f, Neon.Magenta) { v ->
                EqRepository.updateCurrent { it.copy(bassBoost = v) }
            }
        }

        /* ── Toggles ── */
        Panel {
            ToggleRow("LIMITADOR ANTI-CLIPPING", eq.limiter) { on ->
                EqRepository.updateCurrent { it.copy(limiter = on) }
            }
            ToggleRow("AUTO-PREAMP (resta el pico)", eq.autoPreamp) { on ->
                EqRepository.updateCurrent { it.copy(autoPreamp = on) }
            }
            ToggleRow(
                if (app.globalMode && globalUnsupported) "MODO GLOBAL · NO SOPORTADO" else "MODO GLOBAL (experimental)",
                app.globalMode,
                labelColor = if (app.globalMode && globalUnsupported) Neon.Red else Neon.Text,
            ) { on ->
                EqRepository.update { it.copy(globalMode = on) }
            }
        }

        if (hooked == 0 && !app.globalMode) {
            Panel(border = Neon.Amber) {
                Text("SIN SESIÓN DE SPOTIFY", color = Neon.Amber, fontFamily = Neon.Mono,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "1. Dale play en Spotify.\n" +
                        "2. Spotify › Configuración › Calidad del audio › Ecualizador.\n" +
                        "3. Elige NEXUS.EQ si el sistema pregunta.",
                    color = Neon.Text, fontFamily = Neon.Mono, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        }

        Text("Perfil guardado por salida: cada dispositivo recuerda su ajuste.",
            color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp)
    }
}

/* ═══════════ Componentes ═══════════ */

@Composable
private fun Panel(border: Color = Neon.Line, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Neon.Panel)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Neon.Panel)
            .border(1.dp, Neon.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 8.sp)
        Text(value, color = color, fontFamily = Neon.Mono, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    val c = if (on) Neon.Lime else Neon.Dim
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.copy(alpha = 0.12f))
            .border(2.dp, c, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (on) "ON" else "OFF", color = c, fontFamily = Neon.Mono,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Neon.Cyan else Neon.Line
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Neon.Cyan.copy(alpha = 0.12f) else Neon.Panel)
            .border(1.dp, c, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Neon.Cyan else Neon.Text, fontFamily = Neon.Mono, fontSize = 11.sp)
    }
}

@Composable
private fun LabeledSlider(
    label: String, valueText: String, value: Float,
    range: ClosedFloatingPointRange<Float>, color: Color, onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(valueText, color = color, fontFamily = Neon.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = color, activeTrackColor = color, inactiveTrackColor = Neon.Line,
        ),
    )
}

@Composable
private fun ToggleRow(
    label: String, checked: Boolean, labelColor: Color = Neon.Text, onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, fontFamily = Neon.Mono, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Neon.Bg, checkedTrackColor = Neon.Lime,
                uncheckedThumbColor = Neon.Dim, uncheckedTrackColor = Neon.Panel,
                uncheckedBorderColor = Neon.Line,
            ),
        )
    }
}

/** Fader vertical tipo vúmetro LED. Arrastre o toque para fijar; doble toque = 0 dB. */
@Composable
private fun BandFader(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val change by rememberUpdatedState(onChange)
    val range = Bands.MAX_DB - Bands.MIN_DB
    fun yToDb(y: Float, h: Float): Float {
        val frac = 1f - (y / h).coerceIn(0f, 1f)
        return ((Bands.MIN_DB + frac * range) * 2).roundToInt() / 2f
    }
    val valColor = when {
        value > 0 -> Neon.Cyan
        value < 0 -> Neon.Magenta
        else -> Neon.Dim
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (value == 0f) "0" else fmtShort(value), color = valColor,
            fontFamily = Neon.Mono, fontSize = 9.sp)
        Canvas(
            Modifier
                .width(26.dp)
                .height(190.dp)
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { change(0f) },
                        onTap = { change(yToDb(it.y, size.height.toFloat())) },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { c, _ ->
                        c.consume()
                        change(yToDb(c.position.y, size.height.toFloat()))
                    }
                }
        ) {
            val segs = 24
            val gap = 2.dp.toPx()
            val segH = (size.height - gap * (segs - 1)) / segs
            val segW = size.width * 0.62f
            val x0 = (size.width - segW) / 2f
            for (s in 0 until segs) {
                // s = 0 arriba (+12), s = segs-1 abajo (-12)
                val top = s * (segH + gap)
                val dbCenter = Bands.MAX_DB - (s + 0.5f) * (range / segs)
                val lit = (value > 0 && dbCenter in 0f..value) || (value < 0 && dbCenter in value..0f)
                val base = when {
                    dbCenter > 6f -> Neon.Lime
                    dbCenter > 0f -> Neon.Cyan
                    else -> Neon.Magenta
                }
                drawRoundRect(
                    color = if (lit) base else Neon.Line,
                    topLeft = Offset(x0, top),
                    size = Size(segW, segH),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
            // línea de 0 dB
            val zeroY = size.height / 2f
            drawLine(Neon.Dim, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            // thumb
            val thumbY = (1f - (value - Bands.MIN_DB) / range) * size.height
            drawRoundRect(
                color = Neon.Text,
                topLeft = Offset(0f, thumbY - 2.dp.toPx()),
                size = Size(size.width, 4.dp.toPx()),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp)
    }
}

/** Curva de respuesta en escala log 20 Hz – 20 kHz, con glow neón. */
@Composable
private fun ResponseCurve(gains: FloatArray, active: Boolean, modifier: Modifier) {
    val color = if (active) Neon.Cyan else Neon.Dim
    Canvas(modifier.clipToBounds()) {
        val fMin = log10(20f)
        val fMax = log10(20000f)
        // Rango efectivo: -12 (faders) .. +20 (+12 fader +8 bass boost). Evita que la curva se salga.
        val dbMax = 20f
        val dbMin = -12f
        fun yOf(db: Float) = (dbMax - db.coerceIn(dbMin, dbMax)) / (dbMax - dbMin) * size.height

        // grilla
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        listOf(-12f, -6f, 6f, 12f, 18f).forEach {
            drawLine(Neon.Line, Offset(0f, yOf(it)), Offset(size.width, yOf(it)), pathEffect = dash)
        }
        drawLine(Neon.Dim, Offset(0f, yOf(0f)), Offset(size.width, yOf(0f)))
        Bands.CENTERS.forEach { f ->
            val x = (log10(f) - fMin) / (fMax - fMin) * size.width
            drawLine(Neon.Line, Offset(x, 0f), Offset(x, size.height), pathEffect = dash)
        }

        // curva
        val steps = 120
        val line = Path()
        val fill = Path()
        for (i in 0..steps) {
            val x = size.width * i / steps
            val f = 10f.pow(fMin + (fMax - fMin) * i / steps)
            val y = yOf(EqMath.curveAt(gains, f))
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, yOf(0f)); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, yOf(0f)); fill.close()

        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.25f), style = Stroke(width = 8.dp.toPx()))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun fmtDb(v: Float) = (if (v > 0) "+" else "") + String.format("%.1f dB", v)
private fun fmtShort(v: Float) = (if (v > 0) "+" else "") +
    if (abs(v % 1f) < 0.01f) v.toInt().toString() else String.format("%.1f", v)
