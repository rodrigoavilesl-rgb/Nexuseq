package cl.nexus.eq

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

data class Profile(
    val bands: List<Float> = List(10) { 0f },
    val bass: Int = 0,
    val preset: String = "FLAT",
    val preamp: Float = 0f,
    val auto: Boolean = true,
)

enum class Out(val id: String, val label: String) {
    SPK("spk", "PARLANTE"), WIRED("wired", "CABLE"), BT("bt", "BLUETOOTH"), USB("usb", "USB")
}

object Eq {
    val FREQ = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val CENTER = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val BASSW = floatArrayOf(1f, .75f, .42f, .13f, 0f, 0f, 0f, 0f, 0f, 0f)

    val PRESETS: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "FLAT" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "BASS" to listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "METAL" to listOf(4f, 3f, 0f, -3f, -4f, 0f, 3f, 5f, 4f, 3f),
        "ROCK" to listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 5f),
        "ELECTRO" to listOf(6f, 5f, 2f, 0f, -2f, 0f, 1f, 3f, 5f, 6f),
        "POP" to listOf(-1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1f, -2f),
        "VOCAL" to listOf(-3f, -3f, -1f, 2f, 4f, 5f, 4f, 2f, 0f, -1f),
        "CLÁSICA" to listOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -2f, -3f, -5f),
        "RUTA" to listOf(6f, 5f, 3f, 1f, 0f, 1f, 3f, 4f, 3f, 1f),
    )

    /** Ganancia por banda incluyendo el bass boost (hasta +6 dB a 31 Hz). */
    fun eff(p: Profile): List<Float> =
        p.bands.mapIndexed { i, g -> g + p.bass / 100f * 6f * BASSW[i] }

    /** Interpolación Catmull-Rom sobre el índice de banda (las bandas están a una octava). */
    fun sample(a: List<Float>, t: Float): Float {
        val n = a.size
        val i = t.toInt().coerceIn(0, n - 1)
        val f = t - i
        val p0 = a[max(i - 1, 0)]
        val p1 = a[i]
        val p2 = a[minOf(i + 1, n - 1)]
        val p3 = a[minOf(i + 2, n - 1)]
        return 0.5f * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f +
            (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f)
    }

    fun peak(p: Profile): Float {
        val e = eff(p)
        var m = 0f
        var t = 0f
        while (t <= 9f) { m = max(m, sample(e, t)); t += 0.1f }
        return m
    }

    fun preamp(p: Profile): Float = if (p.auto) -peak(p) else p.preamp

    fun matchPreset(b: List<Float>): String =
        PRESETS.entries.firstOrNull { it.value == b }?.key ?: "CUSTOM"
}

/** Estado compartido entre la UI, el servicio y el receptor. */
object EqStore {
    private var prefs: SharedPreferences? = null
    val listeners = CopyOnWriteArrayList<() -> Unit>()

    var profiles by mutableStateOf(Out.values().associate { it.id to Profile() })
    var cur by mutableStateOf("spk")
    var limiter by mutableStateOf(true)
    var global by mutableStateOf(false)
    var sessions by mutableStateOf(setOf<Int>())

    val profile: Profile get() = profiles[cur] ?: Profile()

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("nexuseq", Context.MODE_PRIVATE)
        load()
    }

    fun update(f: (Profile) -> Profile) {
        profiles = profiles + (cur to f(profile))
        changed()
    }

    fun setOutput(id: String) { if (cur != id) { cur = id; changed() } }
    fun enableLimiter(v: Boolean) { limiter = v; changed() }
    fun enableGlobal(v: Boolean) { global = v; changed() }
    fun addSession(id: Int) { sessions = sessions + id; changed() }
    fun removeSession(id: Int) { sessions = sessions - id; changed() }
    fun reset() = update { Profile() }

    private fun changed() {
        save()
        listeners.forEach { it() }
    }

    private fun save() {
        val p = prefs ?: return
        val o = JSONObject()
        profiles.forEach { (k, v) ->
            o.put(k, JSONObject().apply {
                put("bands", JSONArray(v.bands.map { it.toDouble() }))
                put("bass", v.bass); put("preset", v.preset)
                put("preamp", v.preamp.toDouble()); put("auto", v.auto)
            })
        }
        p.edit().putString("profiles", o.toString()).putString("cur", cur)
            .putBoolean("limiter", limiter).putBoolean("global", global).apply()
    }

    private fun load() {
        val p = prefs ?: return
        try {
            val o = JSONObject(p.getString("profiles", "{}") ?: "{}")
            val m = HashMap<String, Profile>()
            Out.values().forEach { out ->
                val j = o.optJSONObject(out.id)
                m[out.id] = if (j == null) Profile() else {
                    val a = j.getJSONArray("bands")
                    Profile(
                        bands = List(10) { i -> a.optDouble(i, 0.0).toFloat() },
                        bass = j.optInt("bass", 0), preset = j.optString("preset", "FLAT"),
                        preamp = j.optDouble("preamp", 0.0).toFloat(), auto = j.optBoolean("auto", true),
                    )
                }
            }
            profiles = m
            cur = p.getString("cur", "spk") ?: "spk"
            limiter = p.getBoolean("limiter", true)
            global = p.getBoolean("global", false)
        } catch (_: Exception) { }
    }
}
