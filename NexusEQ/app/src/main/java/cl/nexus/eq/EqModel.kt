package cl.nexus.eq

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* ───────────── Bandas ───────────── */

/**
 * Distribuciones de bandas del ecualizador. 10 es la original; 8, 16 y 32 se eligen en la app.
 * El de 32 bandas parte en 16 Hz y sube de a 1/3 de octava hasta 20 kHz.
 */
enum class BandLayout(val count: Int, val centers: FloatArray, val labels: List<String>) {
    B8(
        8, floatArrayOf(40f, 100f, 250f, 600f, 1500f, 3500f, 8000f, 16000f),
        listOf("40", "100", "250", "600", "1.5K", "3.5K", "8K", "16K"),
    ),
    B10(
        10, floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
        listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"),
    ),
    B16(
        16, floatArrayOf(31f, 50f, 80f, 125f, 200f, 315f, 500f, 800f, 1250f, 2000f, 3150f, 5000f, 8000f, 10000f, 12500f, 16000f),
        listOf("31", "50", "80", "125", "200", "315", "500", "800", "1.2K", "2K", "3.1K", "5K", "8K", "10K", "12K", "16K"),
    ),
    B32(
        32,
        floatArrayOf(
            16f, 20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f,
            630f, 800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f,
        ),
        listOf(
            "16", "20", "25", "31", "40", "50", "63", "80", "100", "125", "160", "200", "250", "315", "400", "500",
            "630", "800", "1K", "1.2K", "1.6K", "2K", "2.5K", "3.1K", "4K", "5K", "6.3K", "8K", "10K", "12K", "16K", "20K",
        ),
    );

    init {
        check(centers.size == count && labels.size == count) { "layout $name inconsistente" }
    }

    /** DynamicsProcessing define cada banda por su frecuencia de corte SUPERIOR. */
    val cutoffs: FloatArray by lazy {
        FloatArray(count) { i ->
            if (i == count - 1) min(centers[i] * 1.25f, 22000f) else sqrt(centers[i] * centers[i + 1])
        }
    }

    companion object {
        fun forCount(n: Int): BandLayout = entries.firstOrNull { it.count == n } ?: B10
    }
}

/** Atajos de la distribución original de 10 bandas (la usan los presets y los tests). */
object Bands {
    val CENTERS: FloatArray = BandLayout.B10.centers
    val LABELS: List<String> = BandLayout.B10.labels
    const val COUNT = 10
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    val CUTOFFS: FloatArray get() = BandLayout.B10.cutoffs
}

/* ───────────── Apps de música detectadas ───────────── */

/** App que abrió una sesión de audio con nosotros; ok=false si el sistema rechazó el DSP. */
data class AppLink(val pkg: String, val ok: Boolean)

object AppNames {
    private val KNOWN = mapOf(
        "com.spotify.music" to "Spotify",
        "deezer.android.app" to "Deezer",
        "com.apple.android.music" to "Apple Music",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.aspiro.tidal" to "TIDAL",
    )

    /** Nombre legible sin pedir permisos de visibilidad de paquetes. */
    fun label(pkg: String): String = KNOWN[pkg] ?: pkg
}

/* ───────────── Rutas de salida ───────────── */

enum class OutputRoute(val label: String) {
    SPEAKER("PARLANTE"), WIRED("CABLE"), BLUETOOTH("BLUETOOTH"), USB("USB")
}

/* ───────────── Estado de un perfil ───────────── */

data class EqState(
    val gains: List<Float> = List(Bands.COUNT) { 0f },
    val preampDb: Float = 0f,          // -12..+6
    val bassBoost: Float = 0f,         // 0..1
    val limiter: Boolean = true,
    val autoPreamp: Boolean = true,    // resta automáticamente el pico de boost
    val presetName: String = "FLAT",
) {
    /** Distribución de bandas deducida de cuántas ganancias hay guardadas. */
    val layout: BandLayout get() = BandLayout.forCount(gains.size)

    /** Cambia a otra distribución remuestreando la curva (con pérdida de detalle al bajar de bandas). */
    fun withLayout(to: BandLayout): EqState =
        if (to == layout) this else copy(gains = EqMath.resample(gains, layout, to))

    fun toJson() = JSONObject().apply {
        put("gains", JSONArray(gains.map { it.toDouble() }))
        put("preamp", preampDb.toDouble())
        put("bass", bassBoost.toDouble())
        put("limiter", limiter)
        put("autoPreamp", autoPreamp)
        put("preset", presetName)
    }

    companion object {
        fun fromJson(o: JSONObject): EqState {
            val arr = o.optJSONArray("gains")
            val n = BandLayout.forCount(arr?.length() ?: Bands.COUNT).count
            val g = List(n) { i -> arr?.optDouble(i, 0.0)?.toFloat() ?: 0f }
            return EqState(
                gains = g,
                preampDb = o.optDouble("preamp", 0.0).toFloat(),
                bassBoost = o.optDouble("bass", 0.0).toFloat(),
                limiter = o.optBoolean("limiter", true),
                autoPreamp = o.optBoolean("autoPreamp", true),
                presetName = o.optString("preset", "FLAT"),
            )
        }
    }
}

/* ───────────── Presets (dB por banda 31 → 16K) ───────────── */

data class Preset(val name: String, val group: String, val gains: List<Float>)

/**
 * Presets agrupados por carácter. Los de RETRO HI-FI se inspiran en la voz típica de los equipos de
 * antaño (cálido, neutro, loudness, en V). Son interpretaciones, NO copias de ninguna marca.
 */
object Presets {
    private fun p(name: String, group: String, vararg g: Number) = Preset(name, group, g.map { it.toFloat() })

    const val G_METAL = "METAL"
    const val G_CYBER = "CYBER"
    const val G_RETRO = "RETRO HI-FI"
    const val G_DAILY = "DÍA A DÍA"

    val ALL = listOf(
        p("FLAT", G_DAILY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        // METAL: graves firmes, medios recortados, mordida en agudos
        p("METAL", G_METAL, 4, 3, 1, 0, -2, -1, 1, 3, 4, 3),
        p("THRASH", G_METAL, 2, 3, 1, -1, -3, -2, 1, 4, 5, 3),
        p("DOOM", G_METAL, 6, 6, 4, 2, 0, -1, -1, 0, -1, -2),
        p("ROCK", G_METAL, 4, 3, 1, -1, -1, 0, 2, 3, 3, 2),
        // CYBER: sub potente y brillo sintético
        p("ELECTRO", G_CYBER, 5, 4, 1, 0, -1, 1, 0, 1, 3, 4),
        p("BASS", G_CYBER, 6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        p("SYNTH", G_CYBER, 3, 3, 2, 0, -1, 0, 1, 2, 4, 4),
        p("CLUB", G_CYBER, 5, 5, 2, 0, -2, 0, 1, 2, 2, 1),
        // RETRO HI-FI: carácter de equipos clásicos
        p("HI-FI 70", G_RETRO, 2, 2, 1, 0, 0, 0, 0, 1, 2, 2),
        p("CÁLIDO", G_RETRO, 1, 2, 2, 1.5, 0.5, 0, -0.5, -1, -1.5, -2),
        p("STUDIO", G_RETRO, 0, 1, 0, 0, 0, 0, 0, 0, 1, 1),
        p("LOUDNESS", G_RETRO, 5, 4, 2, 0, -0.5, 0, 0, 1, 3, 4),
        p("V-SHAPE", G_RETRO, 4, 3, 1, -1, -2, -1, 0, 2, 3, 3),
        p("JAZZ CLUB", G_RETRO, 2, 2, 2, 1, 0, 0, 0, 1, 1, 1),
        // DÍA A DÍA
        p("POP", G_DAILY, -1, 1, 3, 4, 3, 0, -1, -1, 0, 1),
        p("VOCAL", G_DAILY, -2, -1, 0, 2, 4, 4, 3, 1, 0, -1),
        p("CLÁSICA", G_DAILY, 3, 2, 1, 0, 0, 0, -1, 1, 2, 3),
        p("RUTA", G_DAILY, 3, 2, 0, -1, -1, 0, 1, 2, 3, 2),   // compensa ruido de rodado en auto
    )

    /** Grupos en orden de aparición, para dibujar la lista por secciones. */
    val GROUPS: Map<String, List<Preset>> = ALL.groupBy { it.group }

    /** Los que aparecen como botones grandes en el modo conducción. */
    val DRIVE_NAMES = listOf("FLAT", "BASS", "RUTA", "METAL", "ROCK", "POP", "VOCAL", "CÁLIDO", "LOUDNESS")
    val DRIVE: List<Preset> get() = DRIVE_NAMES.mapNotNull { n -> ALL.firstOrNull { it.name == n } }
}

/* ───────────── Matemática de la curva ───────────── */

object EqMath {
    const val BASS_MAX_DB = 8f

    /** Puntos (frecuencia, peso) del refuerzo de graves; entre ellos se interpola en octavas. */
    private val BASS_F = floatArrayOf(31f, 62f, 125f, 250f, 500f)
    private val BASS_W = floatArrayOf(1f, 0.75f, 0.4f, 0.1f, 0f)

    /** Peso del bass boost para una frecuencia (low-shelf aproximado, válido para cualquier distribución). */
    fun bassWeight(freq: Float): Float {
        if (freq <= BASS_F.first()) return 1f
        if (freq >= BASS_F.last()) return 0f
        val i = BASS_F.indexOfLast { it <= freq }
        val t = (log2(freq) - log2(BASS_F[i])) / (log2(BASS_F[i + 1]) - log2(BASS_F[i]))
        return BASS_W[i] + (BASS_W[i + 1] - BASS_W[i]) * t
    }

    fun effectiveGains(s: EqState): FloatArray {
        val c = s.layout.centers
        return FloatArray(s.gains.size) { i ->
            (s.gains[i] + s.bassBoost * BASS_MAX_DB * bassWeight(c[i])).coerceIn(-12f, 20f)
        }
    }

    fun peakBoost(s: EqState): Float = effectiveGains(s).max().coerceAtLeast(0f)

    /** Refuerzo real de graves (≤125 Hz), sumando bandas y Bass boost. Es lo que muestra el medidor GRAVES. */
    fun lowBoost(s: EqState): Float {
        val c = s.layout.centers
        val g = effectiveGains(s)
        var m = 0f
        for (i in g.indices) if (c[i] <= 125f) m = max(m, g[i])
        return m
    }

    /** Preamp real aplicado: con auto-preamp se descuenta el pico para evitar clipping. */
    fun effectivePreamp(s: EqState): Float =
        if (s.autoPreamp) s.preampDb - peakBoost(s) else s.preampDb

    /** Interpolación en escala log para dibujar la respuesta. [centers] son las frecuencias de las ganancias. */
    fun curveAt(gains: FloatArray, freq: Float, centers: FloatArray = Bands.CENTERS): Float {
        val c = centers
        if (freq <= c.first()) return gains.first()
        if (freq >= c.last()) return gains.last()
        val i = c.indexOfFirst { it >= freq } - 1
        val t = (log10(freq) - log10(c[i])) / (log10(c[i + 1]) - log10(c[i]))
        val s = (1 - kotlin.math.cos(t * Math.PI).toFloat()) / 2f   // suavizado coseno
        return gains[i] + (gains[i + 1] - gains[i]) * s
    }

    /** Pasa una curva de una distribución a otra (pasos de 0,5 dB, dentro de ±12). */
    fun resample(gains: List<Float>, from: BandLayout, to: BandLayout): List<Float> {
        if (from == to) return gains
        val g = gains.toFloatArray()
        return to.centers.map { f ->
            ((curveAt(g, f, from.centers) * 2f).roundToInt() / 2f).coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        }
    }
}

/* ───────────── Estado global + persistencia ───────────── */

data class AppState(
    val enabled: Boolean = true,
    val globalMode: Boolean = false,
    val testerMode: Boolean = false,   // MARAKES TESTER MODE: muestra diagnóstico y reporte
    val skinId: String = "METAL",      // aspecto de la app (ver ui/Theme.kt)
    val accent1: Int = 0,              // color de acento principal (ARGB); 0 = el de la skin
    val accent2: Int = 0,              // color de acento secundario (ARGB); 0 = el de la skin
    val corners: Int = 0,              // 0 = según skin, 1 = cortadas, 2 = redondeadas
    val vizDelay: Int = 15,            // segundos de inactividad para el modo gráfico; 0 = apagado
    val vizStyle: Int = 0,             // 0 barras, 1 onda, 2 radial
    val vizEmphasis: Int = 0,          // 0 ambos, 1 graves, 2 agudos
    val route: OutputRoute = OutputRoute.SPEAKER,
    val profiles: Map<OutputRoute, EqState> = OutputRoute.entries.associateWith { EqState() },
) {
    val current: EqState get() = profiles.getValue(route)
}

object EqRepository {
    private const val PREFS = "nexus_eq"
    private const val KEY = "state_v1"
    private const val SAVE_DELAY_MS = 400L
    private const val MAX_LOG = 40

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    /** Sesiones de audio enganchadas (Spotify, etc.). No se persiste. */
    private val _sessions = MutableStateFlow<Set<Int>>(emptySet())
    val sessions: StateFlow<Set<Int>> = _sessions

    /** Qué app abrió cada sesión de audio (para la pantalla de compatibilidad). No se persiste. */
    private val _links = MutableStateFlow<Map<Int, AppLink>>(emptyMap())
    val links: StateFlow<Map<Int, AppLink>> = _links

    /** Últimos eventos (enlaces, errores del DSP, cambios de salida) para el reporte de tester. */
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    fun log(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _log.update { (it + "$t $msg").takeLast(MAX_LOG) }
    }

    fun clearLog() { _log.value = emptyList() }

    /** El modo global (sesión 0) fue rechazado por el sistema o el fabricante. No se persiste. */
    private val _globalUnsupported = MutableStateFlow(false)
    val globalUnsupported: StateFlow<Boolean> = _globalUnsupported

    private var prefs: SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { writeNow() }

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs?.getString(KEY, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                val profObj = o.optJSONObject("profiles")
                val profiles = OutputRoute.entries.associateWith { r ->
                    profObj?.optJSONObject(r.name)?.let(EqState::fromJson) ?: EqState()
                }
                _state.value = AppState(
                    enabled = o.optBoolean("enabled", true),
                    globalMode = o.optBoolean("globalMode", false),
                    testerMode = o.optBoolean("tester", false),
                    skinId = o.optString("skin", "METAL"),
                    accent1 = o.optInt("accent1", 0),
                    accent2 = o.optInt("accent2", 0),
                    corners = o.optInt("corners", 0),
                    vizDelay = o.optInt("vizDelay", 15),
                    vizStyle = o.optInt("vizStyle", 0),
                    vizEmphasis = o.optInt("vizEmphasis", 0),
                    profiles = profiles,
                )
            }
        }
    }

    fun update(transform: (AppState) -> AppState) {
        _state.update(transform)
        save()
    }

    fun updateCurrent(transform: (EqState) -> EqState) = update { s ->
        s.copy(profiles = s.profiles + (s.route to transform(s.current)))
    }

    /** Los presets están definidos a 10 bandas; se remuestrean a la distribución activa. */
    fun applyPreset(p: Preset) = updateCurrent {
        it.copy(gains = EqMath.resample(p.gains, BandLayout.B10, it.layout), presetName = p.name)
    }

    /** Cambia la distribución de bandas en todos los perfiles (remuestrea sus curvas). */
    fun setLayout(l: BandLayout) = update { s ->
        s.copy(profiles = s.profiles.mapValues { (_, eq) -> eq.withLayout(l) })
    }

    fun setBand(i: Int, db: Float) = updateCurrent {
        it.copy(gains = it.gains.toMutableList().also { g -> g[i] = db }, presetName = "CUSTOM")
    }

    fun setRoute(r: OutputRoute) {
        if (_state.value.route != r) log("SALIDA → ${r.label}")
        _state.update { it.copy(route = r) }
    }

    fun setSessions(ids: Set<Int>) { _sessions.value = ids }

    fun setLink(session: Int, link: AppLink?) = _links.update {
        if (link == null) it - session else it + (session to link)
    }

    fun clearLinks() { _links.value = emptyMap() }

    fun setGlobalUnsupported(v: Boolean) { _globalUnsupported.value = v }

    /** Agrupa escrituras: arrastrar un fader no serializa JSON en cada tick. */
    private fun save() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    /** Escribe ya lo pendiente (al salir de la app o morir el servicio). */
    fun flush() {
        main.removeCallbacks(saveRunnable)
        writeNow()
    }

    private fun writeNow() {
        val s = _state.value
        val o = JSONObject().apply {
            put("enabled", s.enabled)
            put("globalMode", s.globalMode)
            put("tester", s.testerMode)
            put("skin", s.skinId)
            put("accent1", s.accent1)
            put("accent2", s.accent2)
            put("corners", s.corners)
            put("vizDelay", s.vizDelay)
            put("vizStyle", s.vizStyle)
            put("vizEmphasis", s.vizEmphasis)
            put("profiles", JSONObject().apply {
                s.profiles.forEach { (r, eq) -> put(r.name, eq.toJson()) }
            })
        }
        prefs?.edit()?.putString(KEY, o.toString())?.apply()
    }
}
