package cl.nexus.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.nexus.eq.Eq
import cl.nexus.eq.EqStore
import cl.nexus.eq.Out
import kotlin.math.abs
import kotlin.math.roundToInt

private fun fmt(v: Float, signed: Boolean = false): String {
    val x = (v * 10f).roundToInt() / 10f
    val s = String.format("%.1f", x)
    return if (signed && x > 0f) "+$s" else s
}

@Composable
private fun M(
    t: String, c: Color = Neon.dim, s: Int = 10,
    w: FontWeight = FontWeight.Normal, mod: Modifier = Modifier,
) = Text(
    text = t, modifier = mod, color = c, fontSize = s.sp,
    fontFamily = FontFamily.Monospace, fontWeight = w, letterSpacing = 0.5.sp, maxLines = 1,
)

@Composable
private fun Panel(title: String, right: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Neon.panel, RoundedCornerShape(4.dp))
            .border(1.dp, Neon.line, RoundedCornerShape(4.dp))
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            M(title, Neon.dim, 9)
            M(right, Neon.fg, 9)
        }
        content()
    }
}

@Composable
fun EqScreen() {
    val p = EqStore.profile
    Column(
        Modifier.fillMaxSize().background(Neon.bg).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        Tiles()
        Panel("RESPUESTA · 31 Hz – 16 kHz", p.preset) { Curve() }
        Panel("EQ 10 BANDAS · ±12 dB", "DOBLE TOQUE = 0") { Faders() }
        Panel("PRESETS", p.preset) { Presets() }
        Panel("CADENA", "PREAMP → EQ → LIMITADOR") { Controls() }
        Panel("PERFIL POR SALIDA", "CAMBIO AUTOMÁTICO") { Outputs() }
        Box(
            Modifier.border(1.dp, Neon.line, RoundedCornerShape(3.dp))
                .clickable { EqStore.reset() }.padding(horizontal = 10.dp, vertical = 8.dp),
        ) { M("RESTABLECER SALIDA ACTUAL", Neon.dim, 9) }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Header() {
    Column {
        M("NEXUS.EQ", Neon.cyan, 22, FontWeight.Bold)
        M("DSP · DYNAMICSPROCESSING · v1.0", Neon.dim, 10)
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, mod: Modifier) {
    Column(
        mod.background(Neon.panel, RoundedCornerShape(3.dp))
            .border(1.dp, Neon.line, RoundedCornerShape(3.dp)).padding(8.dp),
    ) {
        M(label, Neon.dim, 9)
        M(value, color, 14, FontWeight.Bold, Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun Tiles() {
    val p = EqStore.profile
    val pre = Eq.preamp(p)
    val net = Eq.peak(p) + pre
    val n = EqStore.sessions.size
    val link = when {
        EqStore.global -> "GLOBAL" to Neon.lime
        n > 0 -> "$n SES." to Neon.lime
        else -> "ESPERA" to Neon.amber
    }
    val out = Out.values().firstOrNull { it.id == EqStore.cur } ?: Out.SPK
    val peak = when {
        net > 0.05f && !EqStore.limiter -> "CLIP" to Neon.red
        net > 0.05f -> "${fmt(net, true)} dB" to Neon.amber
        else -> "${fmt(net, true)} dB" to Neon.lime
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tile("ENLACE", link.first, link.second, Modifier.weight(1f))
        Tile("SALIDA", if (out == Out.BT) "BT" else out.label, Neon.cyan, Modifier.weight(1f))
        Tile("PREAMP", "${fmt(pre, true)}dB", Neon.fg, Modifier.weight(1f))
        Tile("PICO", peak.first, peak.second, Modifier.weight(1f))
    }
}

@Composable
private fun Curve() {
    val e = Eq.eff(EqStore.profile)
    Canvas(Modifier.fillMaxWidth().aspectRatio(320f / 130f)) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        fun x(t: Float) = pad + t / 9f * (w - 2 * pad)
        fun y(g: Float) = pad + (12f - g.coerceIn(-12f, 12f)) / 24f * (h - 2 * pad)
        listOf(12f, 6f, 0f, -6f, -12f).forEach {
            drawLine(if (it == 0f) Neon.mid else Neon.line, Offset(pad, y(it)), Offset(w - pad, y(it)), 1f)
        }
        for (i in 0..9) drawLine(Color(0xFF111C27), Offset(x(i.toFloat()), pad), Offset(x(i.toFloat()), h - pad), 1f)
        val path = Path()
        var t = 0f
        var first = true
        while (t <= 9.001f) {
            val px = x(t)
            val py = y(Eq.sample(e, t))
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            t += 0.15f
        }
        val area = Path().apply {
            addPath(path)
            lineTo(x(9f), y(0f)); lineTo(x(0f), y(0f)); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Neon.cyan.copy(alpha = .35f), Neon.cyan.copy(alpha = .02f), Neon.mag.copy(alpha = .3f))))
        drawPath(path, Neon.cyan, style = Stroke(width = 2.5.dp.toPx()))
        e.forEachIndexed { i, g ->
            val col = if (g > 0f) Neon.cyan else if (g < 0f) Neon.mag else Neon.dim
            drawCircle(Neon.bg, 4.dp.toPx(), Offset(x(i.toFloat()), y(g)))
            drawCircle(col, 4.dp.toPx(), Offset(x(i.toFloat()), y(g)), style = Stroke(1.5.dp.toPx()))
        }
    }
}

private const val SEGS = 24

@Composable
private fun Fader(i: Int, g: Float, onSet: (Float) -> Unit, mod: Modifier) {
    val set by rememberUpdatedState(onSet)
    Column(mod, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = (if (g > 0f) "+" else "") + (if (abs(g % 1f) > 0.01f) String.format("%.1f", g) else g.toInt().toString())
        M(label, if (g > 0f) Neon.cyan else if (g < 0f) Neon.mag else Neon.dim, 9)
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .background(Color(0xFF070B10), RoundedCornerShape(2.dp))
                .border(1.dp, Neon.line, RoundedCornerShape(2.dp))
                .pointerInput(Unit) {
                    var last = 0L
                    fun value(y: Float): Float {
                        val v = (1f - y / size.height) * 24f - 12f
                        return ((v * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now = System.currentTimeMillis()
                        if (now - last < 300L) { set(0f); last = 0L } else { last = now; set(value(down.position.y)) }
                        down.consume()
                        var pressed = true
                        while (pressed) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull()
                            if (c == null || !c.pressed) {
                                pressed = false
                            } else {
                                set(value(c.position.y)); c.consume()
                            }
                        }
                    }
                },
        ) {
            val sh = size.height / SEGS
            for (k in 0 until SEGS) {
                val hi = 12 - k
                val lo = hi - 1
                val color = when {
                    g > 0f && lo < g - 0.001f && hi > 0 -> if (hi > 8) Neon.lime else Neon.cyan
                    g < 0f && hi > g + 0.001f && lo < 0 -> Neon.mag
                    k == 11 || k == 12 -> Neon.mid
                    else -> Neon.seg
                }
                drawRect(color, Offset(3.dp.toPx(), k * sh + 0.5f), Size(size.width - 6.dp.toPx(), sh - 1.5f))
            }
        }
        M(Eq.FREQ[i], Neon.dim, 9)
    }
}

@Composable
private fun Faders() {
    val p = EqStore.profile
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 10) {
            Fader(i, p.bands[i], { v ->
                EqStore.update { cur ->
                    val b = cur.bands.toMutableList().also { it[i] = v }
                    cur.copy(bands = b, preset = Eq.matchPreset(b))
                }
            }, Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        M("+12", Neon.dim, 9); M("0", Neon.dim, 9); M("−12", Neon.dim, 9)
    }
}

@Composable
private fun Presets() {
    val p = EqStore.profile
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Eq.PRESETS.forEach { (name, bands) ->
            val on = p.preset == name
            Box(
                Modifier.background(Neon.panel2, RoundedCornerShape(3.dp))
                    .border(1.dp, if (on) Neon.cyan else Neon.line, RoundedCornerShape(3.dp))
                    .clickable { EqStore.update { it.copy(bands = bands, preset = name) } }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) { M(name, if (on) Neon.cyan else Neon.fg, 12, FontWeight.Bold) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            M(title, Neon.fg, 13, FontWeight.Bold)
            M(sub, Neon.dim, 10)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Neon.cyan, checkedTrackColor = Neon.panel2, checkedBorderColor = Neon.cyan,
                uncheckedThumbColor = Neon.dim, uncheckedTrackColor = Neon.seg, uncheckedBorderColor = Neon.line,
            ),
        )
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(34.dp).height(34.dp)
            .background(Neon.panel2, RoundedCornerShape(3.dp))
            .border(1.dp, Neon.line, RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { M(label, if (enabled) Neon.fg else Neon.dim, 16, FontWeight.Bold) }
}

@Composable
private fun Controls() {
    val p = EqStore.profile
    val pre = Eq.preamp(p)
    ToggleRow("AUTO-PREAMP", "Resta el pico de boost", p.auto) { v ->
        EqStore.update { c -> if (c.auto && !v) c.copy(auto = false, preamp = (Eq.preamp(c) * 2f).roundToInt() / 2f) else c.copy(auto = v) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            M("PREAMP", Neon.fg, 13, FontWeight.Bold)
            M(if (p.auto) "Calculado · pico +${fmt(Eq.peak(p))} dB" else "Manual", Neon.dim, 10)
        }
        StepBtn("−", !p.auto && p.preamp > -12f) { EqStore.update { it.copy(preamp = (it.preamp - 0.5f).coerceAtLeast(-12f)) } }
        M("${fmt(pre, true)} dB", Neon.fg, 14, FontWeight.Bold, Modifier.width(78.dp).padding(horizontal = 4.dp))
        StepBtn("+", !p.auto && p.preamp < 0f) { EqStore.update { it.copy(preamp = (it.preamp + 0.5f).coerceAtMost(0f)) } }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            M("BASS BOOST", Neon.fg, 13, FontWeight.Bold)
            M("${p.bass}% · +${fmt(p.bass / 100f * 6f)} dB a 31 Hz", Neon.dim, 10)
        }
        Slider(
            value = p.bass.toFloat(), onValueChange = { v -> EqStore.update { it.copy(bass = v.roundToInt()) } },
            valueRange = 0f..100f, steps = 19, modifier = Modifier.width(140.dp),
            colors = SliderDefaults.colors(
                thumbColor = Neon.cyan, activeTrackColor = Neon.cyan, inactiveTrackColor = Neon.seg,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
    ToggleRow("LIMITADOR", "−1 dBFS · protege la salida", EqStore.limiter) { EqStore.enableLimiter(it) }
    ToggleRow("MODO GLOBAL", "Experimental · sesión 0", EqStore.global) { EqStore.enableGlobal(it) }
}

@Composable
private fun Outputs() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Out.values().forEach { o ->
            val on = EqStore.cur == o.id
            Box(
                Modifier.weight(1f)
                    .background(Neon.panel2, RoundedCornerShape(3.dp))
                    .border(1.dp, if (on) Neon.mag else Neon.line, RoundedCornerShape(3.dp))
                    .clickable { EqStore.setOutput(o.id) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { M(o.label, if (on) Neon.mag else Neon.dim, 9, FontWeight.Bold) }
        }
    }
}
