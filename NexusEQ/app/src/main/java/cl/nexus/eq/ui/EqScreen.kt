package cl.nexus.eq.ui

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.nexus.eq.AppLink
import cl.nexus.eq.AppNames
import cl.nexus.eq.AppState
import cl.nexus.eq.BandLayout
import cl.nexus.eq.Bands
import cl.nexus.eq.EqMath
import cl.nexus.eq.EqRepository
import cl.nexus.eq.Preset
import cl.nexus.eq.Presets
import cl.nexus.eq.TesterReport
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun EqScreen() {
    val app by EqRepository.state.collectAsStateWithLifecycle()
    val sessions by EqRepository.sessions.collectAsStateWithLifecycle()
    val globalUnsupported by EqRepository.globalUnsupported.collectAsStateWithLifecycle()
    val links by EqRepository.links.collectAsStateWithLifecycle()
    val log by EqRepository.log.collectAsStateWithLifecycle()
    val hooked = sessions.count { it != 0 }

    // Aspecto activo: skin base + acentos y esquinas elegidos por el usuario.
    val skin = remember(app.skinId, app.accent1, app.accent2, app.corners) {
        Skins.resolve(app.skinId, app.accent1, app.accent2, app.corners)
    }
    SideEffect { if (Neon.skin != skin) Neon.skin = skin }

    var drive by rememberSaveable { mutableStateOf(false) }
    var splash by rememberSaveable { mutableStateOf(true) }
    var viz by remember { mutableStateOf(false) }

    // Inactividad: se mide sin recomponer (un arreglo plano), y el modo gráfico entra tras N segundos.
    val lastTouch = remember { longArrayOf(System.nanoTime()) }
    LaunchedEffect(app.vizDelay, drive, viz, splash) {
        if (app.vizDelay <= 0 || drive || viz || splash) return@LaunchedEffect
        lastTouch[0] = System.nanoTime()
        while (true) {
            delay(500)
            if (System.nanoTime() - lastTouch[0] >= app.vizDelay * 1_000_000_000L) {
                viz = true
                break
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch[0] = System.nanoTime()
                    }
                }
            }
    ) {
        if (drive) {
            DriveScreen(app, hooked) { drive = false }
        } else {
            MainScreen(app, hooked, globalUnsupported, links, log,
                onDrive = { drive = true },
                onPreviewViz = { viz = true })
        }

        if (viz && !drive) {
            val eq = app.current
            GraphicModeOverlay(
                gains = EqMath.effectiveGains(eq),
                centers = eq.layout.centers,
                style = app.vizStyle,
                emphasis = app.vizEmphasis,
                title = "${eq.presetName} · ${eq.layout.count} BANDAS",
            ) {
                viz = false
                lastTouch[0] = System.nanoTime()
            }
        }

        if (splash) SplashOverlay { splash = false }
    }
}

/* ═══════════ Pantalla principal ═══════════ */

@Composable
private fun MainScreen(
    app: AppState,
    hooked: Int,
    globalUnsupported: Boolean,
    links: Map<Int, AppLink>,
    log: List<String>,
    onDrive: () -> Unit,
    onPreviewViz: () -> Unit,
) {
    val ctx = LocalContext.current
    val eq = app.current
    val layout = eq.layout
    val gains = EqMath.effectiveGains(eq)
    val preamp = EqMath.effectivePreamp(eq)
    val peak = EqMath.peakBoost(eq)
    val low = EqMath.lowBoost(eq)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /* ── Header ── */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GlitchTitle("NEXUS.EQ")
                Text("METAL · CYBER · DSP ${layout.count} BANDAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            }
            Chip("DRIVE") { onDrive() }
            Spacer(Modifier.width(10.dp))
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
            StatTile("LIMIT", if (eq.limiter) "ON" else "OFF",
                if (eq.limiter) Neon.Lime else Neon.Dim, Modifier.weight(1f))
        }

        /* ── Medidores (GRAVES mide el refuerzo real: bandas bajas + Bass boost) ── */
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ArcGauge("PREAMP", fmtDb(preamp), (preamp + 24f) / 30f,
                    if (preamp > 0) Neon.Red else Neon.Cyan)
                ArcGauge("GRAVES", fmtDb(low), low / 20f,
                    if (low > 10f) Neon.Red else Neon.Magenta)
                ArcGauge("PICO", fmtDb(peak), peak / 20f,
                    when {
                        peak > 10f -> Neon.Red
                        peak > 5f -> Neon.Amber
                        else -> Neon.Lime
                    })
            }
        }

        /* ── Curva de respuesta ── */
        Panel {
            Text("RESPUESTA · ${eq.presetName}", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            ResponseCurve(gains, layout.centers, active = app.enabled, modifier = Modifier.fillMaxWidth().height(110.dp))
        }

        /* ── Faders (8 / 10 / 16 / 32 bandas) ── */
        Panel {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BANDAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
                BandLayout.entries.forEach { l ->
                    Chip("${l.count}", selected = layout == l) { EqRepository.setLayout(l) }
                }
            }
            if (layout.count <= 10) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until layout.count) {
                        BandFader(
                            label = layout.labels[i],
                            value = eq.gains[i],
                            onChange = { EqRepository.setBand(i, it) },
                            modifier = Modifier.weight(1f),
                            compact = false,
                        )
                    }
                }
            } else {
                // 16 y 32 bandas: faders más angostos con desplazamiento horizontal.
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    for (i in 0 until layout.count) {
                        BandFader(
                            label = layout.labels[i],
                            value = eq.gains[i],
                            onChange = { EqRepository.setBand(i, it) },
                            modifier = Modifier.width(30.dp),
                            compact = true,
                        )
                    }
                }
            }
            Text(
                if (layout.count <= 10) "Doble toque en una banda = 0 dB"
                else "Desliza para ver todas las bandas · doble toque = 0 dB",
                color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }

        /* ── Presets por grupo ── */
        Presets.GROUPS.forEach { (group, list) ->
            Text(group, color = if (group == Presets.G_METAL) Neon.Red else Neon.Dim,
                fontFamily = Neon.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { p: Preset ->
                    Chip(p.name, selected = eq.presetName == p.name) { EqRepository.applyPreset(p) }
                }
            }
        }
        Text("Los presets RETRO HI-FI imitan el carácter típico de equipos clásicos (cálido, neutro, loudness, en V). Son interpretaciones, no copias de ninguna marca.",
            color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp, lineHeight = 13.sp)

        /* ── Preamp / Refuerzo de graves ── */
        Panel {
            LabeledSlider("PREAMP", fmtDb(eq.preampDb), eq.preampDb, -12f..6f, Neon.Cyan) { v ->
                EqRepository.updateCurrent { it.copy(preampDb = (v * 2).roundToInt() / 2f) }
            }
            LabeledSlider("REFUERZO DE GRAVES", "${(eq.bassBoost * 100).roundToInt()}%", eq.bassBoost, 0f..1f, Neon.Magenta) { v ->
                EqRepository.updateCurrent { it.copy(bassBoost = v) }
            }
            Text("Se suma a los graves de las bandas y del preset. El medidor GRAVES muestra el total.",
                color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp)
        }

        /* ── Aspecto: skins, acentos y esquinas ── */
        Panel {
            Text("ASPECTO", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Skins.ALL.forEach { sk ->
                    Chip(sk.name, selected = app.skinId == sk.id) {
                        // al elegir una skin se parte de su aspecto original
                        EqRepository.update { it.copy(skinId = sk.id, accent1 = 0, accent2 = 0, corners = 0) }
                    }
                }
            }
            Text("ACENTO PRINCIPAL", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            SwatchRow(app.accent1) { c -> EqRepository.update { it.copy(accent1 = c) } }
            Text("ACENTO SECUNDARIO", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            SwatchRow(app.accent2) { c -> EqRepository.update { it.copy(accent2 = c) } }
            Text("ESQUINAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("SEGÚN SKIN", selected = app.corners == 0) { EqRepository.update { it.copy(corners = 0) } }
                Chip("CORTADAS", selected = app.corners == 1) { EqRepository.update { it.copy(corners = 1) } }
                Chip("REDONDAS", selected = app.corners == 2) { EqRepository.update { it.copy(corners = 2) } }
            }
        }

        /* ── Modo gráfico (salvapantallas animado) ── */
        Panel {
            Text("MODO GRÁFICO", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
            Text("Entra solo tras unos segundos sin tocar la pantalla. Un toque lo cierra.",
                color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "APAGADO", 15 to "15 s", 30 to "30 s", 60 to "60 s").forEach { (sec, name) ->
                    Chip(name, selected = app.vizDelay == sec) { EqRepository.update { it.copy(vizDelay = sec) } }
                }
            }
            Text("ESTILO", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BARRAS", "ONDA", "RADIAL").forEachIndexed { i, name ->
                    Chip(name, selected = app.vizStyle == i) { EqRepository.update { it.copy(vizStyle = i) } }
                }
            }
            Text("ÉNFASIS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AMBOS", "GRAVES", "AGUDOS").forEachIndexed { i, name ->
                    Chip(name, selected = app.vizEmphasis == i) { EqRepository.update { it.copy(vizEmphasis = i) } }
                }
            }
            Row(Modifier.padding(top = 12.dp)) { Chip("PROBAR AHORA") { onPreviewViz() } }
            Text("La animación sigue tu curva de EQ y no escucha el audio.",
                color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
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
            ToggleRow("MARAKES TESTER MODE", app.testerMode,
                labelColor = if (app.testerMode) Neon.Red else Neon.Text) { on ->
                EqRepository.update { it.copy(testerMode = on) }
            }
        }

        /* ── Compatibilidad: qué apps se enlazaron ── */
        if (links.isNotEmpty()) {
            Panel {
                Text("APPS DETECTADAS", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
                links.values.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(AppNames.label(l.pkg), color = Neon.Text, fontFamily = Neon.Mono,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (l.ok) "ENLAZADA" else "ERROR DSP",
                            color = if (l.ok) Neon.Lime else Neon.Red, fontFamily = Neon.Mono,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        /* ── Modo tester: reporte para compartir ── */
        if (app.testerMode) {
            val report = TesterReport.build(appVersion(ctx), deviceInfo(), app, links, log)
            Panel(border = Neon.Red) {
                Text("MARAKES TESTER MODE", color = Neon.Red, fontFamily = Neon.Mono,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(report, color = Neon.Text, fontFamily = Neon.Mono, fontSize = 10.sp,
                    lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("COMPARTIR REPORTE") { shareText(ctx, report) }
                    Chip("BORRAR LOG") { EqRepository.clearLog() }
                }
            }
        }

        if (hooked == 0 && !app.globalMode) {
            Panel(border = Neon.Amber) {
                Text("SIN SESIÓN DE AUDIO", color = Neon.Amber, fontFamily = Neon.Mono,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "1. Reproduce música (Spotify, Deezer, YouTube Music…).\n" +
                        "2. En esa app abre su Ecualizador (en Spotify: Configuración › Calidad del audio › Ecualizador).\n" +
                        "3. Elige NEXUS.EQ si el sistema pregunta.",
                    color = Neon.Text, fontFamily = Neon.Mono, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        }

        Text("Perfil guardado por salida: cada dispositivo recuerda su ajuste.",
            color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp)
    }
}

/* ═══════════ Modo conducción ═══════════ */

@Composable
private fun DriveScreen(app: AppState, hooked: Int, onExit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true               // la pantalla no se apaga mientras conduces
        onDispose { view.keepScreenOn = false }
    }
    val eq = app.current
    val linked = hooked > 0 || app.globalMode

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MODO CONDUCCIÓN", color = Neon.Red, fontFamily = Neon.Mono,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                Text("${app.route.label} · ${eq.presetName} · " + (if (linked) "ENLAZADO" else "ESPERA"),
                    color = if (linked) Neon.Lime else Neon.Amber, fontFamily = Neon.Mono, fontSize = 12.sp)
            }
            Chip("SALIR") { onExit() }
        }

        // Botón gigante ON/OFF
        val c = if (app.enabled) Neon.Lime else Neon.Dim
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(Neon.Cut)
                .background(c.copy(alpha = 0.12f))
                .border(3.dp, c, Neon.Cut)
                .clickable { EqRepository.update { it.copy(enabled = !it.enabled) } },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (app.enabled) "EQ ON" else "EQ OFF", color = c, fontFamily = Neon.Mono,
                fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 4.sp)
        }

        // Presets grandes, 3 por fila
        Presets.DRIVE.chunked(3).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowPresets.forEach { p ->
                    val sel = eq.presetName == p.name
                    Box(
                        Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clip(Neon.Cut)
                            .background(if (sel) Neon.Cyan.copy(alpha = 0.15f) else Neon.Panel)
                            .border(2.dp, if (sel) Neon.Cyan else Neon.Line, Neon.Cut)
                            .clickable { EqRepository.applyPreset(p) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.name, color = if (sel) Neon.Cyan else Neon.Text, fontFamily = Neon.Mono,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
                // relleno para filas incompletas
                repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ═══════════ Componentes ═══════════ */

/** Título con aberración cromática (acento 1 / acento 2) sobre cromo: look glitch. */
@Composable
private fun GlitchTitle(text: String) {
    Box {
        Text(text, color = Neon.Magenta.copy(alpha = 0.85f), fontFamily = Neon.Mono,
            fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp,
            modifier = Modifier.offset(x = (-1.5).dp))
        Text(text, color = Neon.Cyan.copy(alpha = 0.85f), fontFamily = Neon.Mono,
            fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp,
            modifier = Modifier.offset(x = 1.5.dp))
        Text(text, color = Neon.Chrome, fontFamily = Neon.Mono,
            fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp)
    }
}

@Composable
private fun Panel(border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val borderBrush = if (border != null) Brush.linearGradient(listOf(border, border))
    else Brush.linearGradient(listOf(Neon.Red.copy(alpha = 0.7f), Neon.Line, Neon.Line, Neon.Cyan.copy(alpha = 0.5f)))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Neon.Cut)
            .background(Brush.verticalGradient(listOf(Neon.PanelHi, Neon.Panel)))
            .border(1.dp, borderBrush, Neon.Cut)
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(Neon.CutSmall)
            .background(Neon.Panel)
            .border(1.dp, Neon.Line, Neon.CutSmall)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 8.sp)
        Text(value, color = color, fontFamily = Neon.Mono, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Medidor en arco de 270° (estilo reloj deportivo). frac en 0..1. */
@Composable
private fun ArcGauge(label: String, valueText: String, frac: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(Neon.Line, 135f, 270f, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                val f = frac.coerceIn(0f, 1f)
                if (f > 0.005f) {
                    drawArc(color.copy(alpha = 0.25f), 135f, 270f * f, false, topLeft, arcSize,
                        style = Stroke(stroke + 5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(color, 135f, 270f * f, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(valueText, color = color, fontFamily = Neon.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 9.sp)
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    val c = if (on) Neon.Lime else Neon.Dim
    Box(
        Modifier
            .size(50.dp)
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
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = if (selected) Neon.Cyan else Neon.Line
    Box(
        Modifier
            .clip(Neon.CutSmall)
            .background(if (selected) Neon.Cyan.copy(alpha = 0.14f) else Neon.Panel)
            .border(1.dp, c, Neon.CutSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Neon.Cyan else Neon.Text, fontFamily = Neon.Mono, fontSize = 11.sp, maxLines = 1)
    }
}

/** Fila de círculos de color para elegir un acento; el primero ("A") vuelve al de la skin. */
@Composable
private fun SwatchRow(selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Neon.Panel)
                .border(if (selected == 0) 3.dp else 1.dp, if (selected == 0) Neon.Chrome else Neon.Line, CircleShape)
                .clickable { onPick(0) },
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = Neon.Dim, fontFamily = Neon.Mono, fontSize = 10.sp)
        }
        Skins.SWATCHES.forEach { c ->
            val argb = c.toArgb()
            val sel = selected == argb
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (sel) 3.dp else 1.dp, if (sel) Neon.Chrome else Neon.Line, CircleShape)
                    .clickable { onPick(argb) }
            )
        }
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
private fun BandFader(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier, compact: Boolean) {
    val change by rememberUpdatedState(onChange)
    val range = Bands.MAX_DB - Bands.MIN_DB
    fun yToDb(y: Float, h: Float): Float {
        val frac = 1f - (y / h).coerceIn(0f, 1f)
        return ((Bands.MIN_DB + frac * range) * 2).roundToInt() / 2f
    }
    val valColor = when {
        value > 6f -> Neon.Red
        value > 0 -> Neon.Cyan
        value < 0 -> Neon.Amber
        else -> Neon.Dim
    }
    val textSize = if (compact) 8.sp else 9.sp

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (value == 0f) "0" else fmtShort(value), color = valColor,
            fontFamily = Neon.Mono, fontSize = textSize, maxLines = 1, softWrap = false)
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
                // calor: acento 1 → acento 2 → alerta al subir; aviso al recortar
                val base = when {
                    dbCenter > 8f -> Neon.Red
                    dbCenter > 4f -> Neon.Magenta
                    dbCenter > 0f -> Neon.Cyan
                    else -> Neon.Amber
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
            drawLine(Neon.Steel, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            // perilla cromada
            val thumbY = (1f - (value - Bands.MIN_DB) / range) * size.height
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Neon.Chrome, Neon.Steel)),
                topLeft = Offset(0f, thumbY - 3.dp.toPx()),
                size = Size(size.width, 6.dp.toPx()),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
        Text(label, color = Neon.Dim, fontFamily = Neon.Mono, fontSize = textSize, maxLines = 1, softWrap = false)
    }
}

/** Curva de respuesta en escala log 20 Hz – 20 kHz, con glow neón. [centers] = frecuencias de las bandas. */
@Composable
private fun ResponseCurve(gains: FloatArray, centers: FloatArray, active: Boolean, modifier: Modifier) {
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
        drawLine(Neon.Steel, Offset(0f, yOf(0f)), Offset(size.width, yOf(0f)))
        centers.forEach { f ->
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
            val y = yOf(EqMath.curveAt(gains, f, centers))
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, yOf(0f)); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, yOf(0f)); fill.close()

        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.25f), style = Stroke(width = 8.dp.toPx()))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/* ═══════════ Utilidades ═══════════ */

private fun fmtDb(v: Float) = (if (v > 0) "+" else "") + String.format("%.1f dB", v)
private fun fmtShort(v: Float) = (if (v > 0) "+" else "") +
    if (abs(v % 1f) < 0.01f) v.toInt().toString() else String.format("%.1f", v)

private fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

private fun deviceInfo(): String =
    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

private fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Compartir reporte").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
