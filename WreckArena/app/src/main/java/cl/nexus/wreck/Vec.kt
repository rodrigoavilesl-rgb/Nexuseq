package cl.nexus.wreck

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val TAU = 6.2831855f
const val PI_F = 3.1415927f

data class V(val x: Float, val y: Float) {
    operator fun plus(o: V) = V(x + o.x, y + o.y)
    operator fun minus(o: V) = V(x - o.x, y - o.y)
    operator fun times(s: Float) = V(x * s, y * s)
    fun dot(o: V) = x * o.x + y * o.y
    fun len() = sqrt(x * x + y * y)
    fun norm(): V {
        val l = len()
        return if (l > 1e-6f) V(x / l, y / l) else V(0f, 0f)
    }
    fun rot(a: Float): V {
        val c = cos(a)
        val s = sin(a)
        return V(x * c - y * s, x * s + y * c)
    }
}

/** Diferencia angular normalizada a [-PI, PI]. */
fun angleDiff(from: Float, to: Float): Float {
    var d = (to - from) % TAU
    if (d > PI_F) d -= TAU
    if (d < -PI_F) d += TAU
    return d
}
