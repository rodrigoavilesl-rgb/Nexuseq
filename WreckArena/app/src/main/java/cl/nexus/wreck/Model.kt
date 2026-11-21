package cl.nexus.wreck

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Escala: 1 unidad = 0.1 m. Un auto de 52 u ≈ 5.2 m; 560 u/s ≈ 200 km/h.
 * g ≈ 98 u/s². Masa normalizada a 1 para autos (todos iguales).
 */

enum class Zone { FRONT, REAR, LEFT, RIGHT }
enum class PickupType { REPAIR, NITRO }
enum class FxKind { SPARK, SMOKE, BLOOD, DEBRIS, SKID }

/** Cuerpo rígido rectangular (OBB). invM = 0 significa estático. */
interface Body {
    var pos: V
    var vel: V
    var omega: Float
    var ang: Float
    val hx: Float
    val hy: Float
    val invM: Float
    val invI: Float
}

class Box(val x: Float, val y: Float, val w: Float, val h: Float) : Body {
    override var pos = V(x + w / 2f, y + h / 2f)
    override var vel = V(0f, 0f)
    override var omega = 0f
    override var ang = 0f
    override val hx = w / 2f
    override val hy = h / 2f
    override val invM = 0f
    override val invI = 0f
}

class Car(
    val id: Int,
    val isPlayer: Boolean,
    val color: Int,
    pos0: V,
    ang0: Float,
) : Body {
    override var pos = pos0
    override var vel = V(0f, 0f)
    override var omega = 0f
    override var ang = ang0
    override val hx = 26f
    override val hy = 13f
    override val invM = 1f
    override val invI = 1f / 282.7f          // m·(w²+h²)/12 con m = 1

    var throttle = 0f                         // -1..1
    var steer = 0f                            // -1..1
    var wheelAngle = 0f                       // ángulo real de las ruedas delanteras (rad)
    var latSpeed = 0f                         // velocidad lateral (para marcas de derrape)
    var nitro = if (isPlayer) 100f else 0f
    var nitroOn = false
    var hull = 100f
    val zones = FloatArray(4) { 100f }       // índice = Zone.ordinal
    val detached = BooleanArray(4)
    var alive = true
    var respawnT = 0f
    var flash = 0f
    var aiStuckT = 0f
    var aiReverseT = 0f

    fun forward() = V(cos(ang), sin(ang))
    fun powerFactor() = if (detached[Zone.FRONT.ordinal]) 0.72f else 1f
}

class Ped(var pos: V, val color: Int) {
    var vel = V(0f, 0f)
    var alive = true
    var timer = 0f
    var stunT = 0f
    var wander = V(0f, 0f)
    var wanderT = 0f
}

class Pickup(val pos: V, val type: PickupType) {
    var life = 20f
}

class Fx(
    var pos: V,
    var vel: V,
    val kind: FxKind,
    val life: Float,
    val size: Float,
    val color: Int,
) {
    var t = life
}

/** Estado de controles táctiles, escrito por el hilo de UI y leído por el loop. */
data class Input(
    var gas: Boolean = false,
    var brake: Boolean = false,
    var nitro: Boolean = false,
    var steer: Float = 0f,
)

fun closestOnBox(p: V, b: Box) = V(
    p.x.coerceIn(b.x, b.x + b.w),
    p.y.coerceIn(b.y, b.y + b.h),
)

fun distToBox(p: V, b: Box) = (p - closestOnBox(p, b)).len()

object Palette {
    val BG = 0xFF05020C.toInt()
    val GRID = 0x2200F0FF.toInt()
    val WALL_FILL = 0xFF0E0820.toInt()
    val BODY = 0xFF0B0716.toInt()
    val WHEEL = 0xFF1A1A22.toInt()
    val WRECK = 0xFF141414.toInt()
    val BLOOD = 0xFFB0001E.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val CYAN = 0xFF00F0FF.toInt()
    val MAGENTA = 0xFFFF2BD6.toInt()
    val LIME = 0xFF39FF14.toInt()
    val ORANGE = 0xFFFF8A00.toInt()
    val YELLOW = 0xFFFFE600.toInt()
    val RED = 0xFFFF2A4D.toInt()
    val VIOLET = 0xFF7B2CFF.toInt()

    val PLAYER = CYAN
    val AI = listOf(MAGENTA, YELLOW, LIME, VIOLET, ORANGE, RED)
    val PEDS = listOf(0xFFEFE7FF.toInt(), 0xFFFFB3E6.toInt(), 0xFFB3F7FF.toInt())
}

fun withA(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

fun healthColor(hp: Float): Int = when {
    hp > 0.6f -> Palette.LIME
    hp > 0.3f -> Palette.YELLOW
    else -> Palette.RED
}

/** Geometría de botones táctiles compartida por input y render. */
object Layout {
    fun btnR(w: Float, h: Float) = min(w, h) * 0.13f
    fun gas(w: Float, h: Float) = V(w * 0.87f, h * 0.70f)
    fun brake(w: Float, h: Float) = V(w * 0.70f, h * 0.70f)
    fun nitro(w: Float, h: Float) = V(w * 0.87f, h * 0.36f)
    fun steerCenter(w: Float) = w * 0.225f
    fun steerHalf(w: Float) = w * 0.18f
    fun steerZoneEnd(w: Float) = w * 0.45f

    fun inside(x: Float, y: Float, c: V, r: Float): Boolean {
        val dx = x - c.x
        val dy = y - c.y
        return dx * dx + dy * dy <= r * r
    }
}

