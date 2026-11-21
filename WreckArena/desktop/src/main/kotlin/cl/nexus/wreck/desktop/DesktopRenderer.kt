package cl.nexus.wreck.desktop

import cl.nexus.wreck.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Port de Renderer (Android) a Java2D. Misma escena y HUD. */
class DesktopRenderer {

    private lateinit var g: Graphics2D
    private var filled = true
    private var camX = 0f
    private var camY = 0f
    private var camReady = false

    private fun fillC(c: Int) {
        filled = true
        g.color = Color(c, true)
    }

    private fun strokeC(c: Int, w: Float) {
        filled = false
        g.color = Color(c, true)
        g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }

    private fun shape(s: Shape) = if (filled) g.fill(s) else g.draw(s)

    private fun rect(l: Float, t: Float, r: Float, b: Float) =
        shape(Rectangle2D.Float(min(l, r), min(t, b), abs(r - l), abs(b - t)))

    private fun circle(x: Float, y: Float, r: Float) =
        shape(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r))

    private fun roundRect(l: Float, t: Float, r: Float, b: Float, rx: Float) =
        shape(RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, rx * 2))

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        g.draw(Line2D.Float(x1, y1, x2, y2))

    /** Arco en convención Android (grados, horario); Java2D usa antihorario. */
    private fun arc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) =
        g.draw(Arc2D.Float(l, t, r - l, b - t, -start, -sweep, Arc2D.OPEN))

    private fun text(s: String, x: Float, y: Float, size: Float, color: Int, center: Boolean = true) {
        g.font = Font(Font.MONOSPACED, Font.BOLD, max(1, size.roundToInt()))
        g.color = Color(color, true)
        val w = g.fontMetrics.stringWidth(s)
        g.drawString(s, if (center) x - w / 2f else x, y)
    }

    fun draw(gfx: Graphics2D, w: Int, h: Int, world: World, input: Input) {
        g = gfx
        val W = w.toFloat()
        val H = h.toFloat()
        val s = min(H / 700f, W / 1000f)
        val pl = world.player

        val tx = pl.pos.x + pl.vel.x * 0.25f
        val ty = pl.pos.y + pl.vel.y * 0.25f
        if (!camReady) {
            camX = tx; camY = ty; camReady = true
        }
        camX += (tx - camX) * 0.12f
        camY += (ty - camY) * 0.12f

        val shakePx = world.shake * 9f
        val jx = (Random.nextFloat() - 0.5f) * shakePx
        val jy = (Random.nextFloat() - 0.5f) * shakePx
        fun sx(x: Float) = (x - camX) * s + W / 2f + jx
        fun sy(y: Float) = (y - camY) * s + H / 2f + jy

        fillC(Palette.BG)
        rect(0f, 0f, W, H)

        // grilla
        strokeC(Palette.GRID, 1f)
        val step = 100f
        val halfW = W / 2f / s
        val halfH = H / 2f / s
        var gx = floor((camX - halfW) / step) * step
        while (gx <= camX + halfW) {
            line(sx(gx), 0f, sx(gx), H); gx += step
        }
        var gy = floor((camY - halfH) / step) * step
        while (gy <= camY + halfH) {
            line(0f, sy(gy), W, sy(gy)); gy += step
        }

        // muros
        for (b in world.boxes) {
            val x0 = sx(b.x); val y0 = sy(b.y)
            val x1 = sx(b.x + b.w); val y1 = sy(b.y + b.h)
            fillC(Palette.WALL_FILL)
            rect(x0, y0, x1, y1)
            strokeC(withA(Palette.MAGENTA, 60), 8f)
            rect(x0, y0, x1, y1)
            strokeC(Palette.MAGENTA, 2f)
            rect(x0, y0, x1, y1)
        }

        // pickups
        val pulse = 1f + 0.15f * sin(world.time * 6f)
        for (pk in world.pickups) {
            val x = sx(pk.pos.x); val y = sy(pk.pos.y)
            val col = if (pk.type == PickupType.REPAIR) Palette.LIME else Palette.ORANGE
            fillC(withA(col, 70))
            circle(x, y, 22f * s * pulse)
            fillC(col)
            circle(x, y, 10f * s)
            text(if (pk.type == PickupType.REPAIR) "+" else "N", x, y + 5f * s, 14f * s, Palette.BG)
        }

        // peatones
        for (pd in world.peds) {
            fillC(
                if (pd.alive) pd.color
                else withA(Palette.BLOOD, (255f * (pd.timer / 2.5f)).coerceIn(0f, 255f).toInt())
            )
            circle(sx(pd.pos.x), sy(pd.pos.y), World.PED_R * s)
        }

        // autos
        for (car in world.cars) drawCar(car, sx(car.pos.x), sy(car.pos.y), s)

        // partículas
        for (f in world.fx) {
            val k = (f.t / f.life).coerceIn(0f, 1f)
            val x = sx(f.pos.x); val y = sy(f.pos.y)
            when (f.kind) {
                FxKind.SMOKE -> {
                    fillC(withA(f.color, (60f * k).toInt()))
                    circle(x, y, f.size * s * (1.6f - k))
                }
                FxKind.SPARK -> {
                    fillC(withA(f.color, (255f * k).toInt()))
                    circle(x, y, f.size * s * k + 1f)
                }
                FxKind.BLOOD -> {
                    fillC(withA(Palette.BLOOD, (220f * k).toInt()))
                    circle(x, y, f.size * s)
                }
                FxKind.SKID -> {
                    fillC(withA(Palette.BODY, (110f * k).toInt()))
                    circle(x, y, f.size * s)
                }
                FxKind.DEBRIS -> {
                    val hs = f.size * s / 2f
                    fillC(withA(f.color, (255f * k).toInt()))
                    rect(x - hs, y - hs, x + hs, y + hs)
                }
            }
        }

        drawHud(W, H, world, input)
    }

    private fun drawCar(car: Car, x: Float, y: Float, s: Float) {
        val hl = 26f * s
        val hw = 13f * s
        val saved = g.transform
        g.translate(x.toDouble(), y.toDouble())
        g.rotate(car.ang.toDouble())
        try {
            if (!car.alive) {
                fillC(Palette.WRECK)
                roundRect(-hl, -hw, hl, hw, 6f * s)
                strokeC(withA(Palette.ORANGE, 110), 1.5f * s)
                roundRect(-hl, -hw, hl, hw, 6f * s)
                return
            }

            // halo
            fillC(withA(car.color, if (car.flash > 0f) 230 else 60))
            roundRect(-hl - 3f * s, -hw - 3f * s, hl + 3f * s, hw + 3f * s, 9f * s)

            // cuerpo
            fillC(if (car.flash > 0f) Palette.WHITE else Palette.BODY)
            roundRect(-hl, -hw, hl, hw, 7f * s)

            // zonas de daño
            val t = 5f * s
            for (i in 0 until 4) {
                if (car.detached[i]) continue
                fillC(withA(healthColor(car.zones[i] / 100f), 230))
                when (i) {
                    Zone.FRONT.ordinal -> rect(hl - t, -hw + 2f * s, hl - s, hw - 2f * s)
                    Zone.REAR.ordinal -> rect(-hl + s, -hw + 2f * s, -hl + t, hw - 2f * s)
                    Zone.LEFT.ordinal -> rect(-hl + 4f * s, -hw + s, hl - 4f * s, -hw + t * 0.6f)
                    else -> rect(-hl + 4f * s, hw - t * 0.6f, hl - 4f * s, hw - s)
                }
            }

            // ruedas (las delanteras giran con la dirección real)
            drawWheel(hl * 0.62f, -hw - 1.5f * s, car.wheelAngle, s)
            drawWheel(hl * 0.62f, hw + 1.5f * s, car.wheelAngle, s)
            drawWheel(-hl * 0.62f, -hw - 1.5f * s, 0f, s)
            drawWheel(-hl * 0.62f, hw + 1.5f * s, 0f, s)

            // parabrisas
            fillC(withA(Palette.CYAN, 90))
            rect(hl * 0.12f, -hw * 0.65f, hl * 0.45f, hw * 0.65f)

            // contorno neón
            strokeC(car.color, 2f * s)
            roundRect(-hl, -hw, hl, hw, 7f * s)

            // faros y nitro
            fillC(Palette.YELLOW)
            circle(hl - 2f * s, -hw * 0.55f, 1.8f * s)
            circle(hl - 2f * s, hw * 0.55f, 1.8f * s)
            if (car.nitroOn) {
                fillC(Palette.ORANGE)
                rect(-hl - 9f * s, -hw * 0.4f, -hl - s, hw * 0.4f)
            }
        } finally {
            g.transform = saved
        }
    }

    private fun drawWheel(x: Float, y: Float, angle: Float, s: Float) {
        val saved = g.transform
        g.translate(x.toDouble(), y.toDouble())
        g.rotate(angle.toDouble())
        fillC(Palette.WHEEL)
        rect(-5f * s, -2.5f * s, 5f * s, 2.5f * s)
        g.transform = saved
    }

    private fun drawHud(W: Float, H: Float, world: World, input: Input) {
        val u = min(W / 1600f, H / 900f)
        val pl = world.player

        // velocímetro
        val cx = 140f * u
        val cy = 140f * u
        val r = 96f * u
        val kmh = pl.vel.len() * 0.36f
        val frac = (kmh / 220f).coerceIn(0f, 1f)
        strokeC(withA(Palette.CYAN, 40), 12f * u)
        arc(cx - r, cy - r, cx + r, cy + r, 135f, 270f)
        strokeC(speedColor(frac), 12f * u)
        arc(cx - r, cy - r, cx + r, cy + r, 135f, 270f * frac)
        text("${kmh.toInt()}", cx, cy + 12f * u, 52f * u, Palette.WHITE)
        text("KM/H", cx, cy + 40f * u, 14f * u, Palette.CYAN)

        // nitro
        fillC(withA(Palette.WHITE, 30))
        rect(40f * u, 270f * u, 240f * u, 282f * u)
        fillC(Palette.ORANGE)
        rect(40f * u, 270f * u, 40f * u + 200f * u * (pl.nitro / 100f), 282f * u)
        text("NOS", 140f * u, 262f * u, 14f * u, Palette.ORANGE)

        // tiempo / score
        val t = world.timeLeft.coerceAtLeast(0f).toInt()
        text("%d:%02d".format(t / 60, t % 60), W / 2f, 66f * u, 58f * u, if (t <= 10) Palette.RED else Palette.CYAN)
        text("SCORE ${world.score}", W / 2f, 104f * u, 24f * u, Palette.WHITE)
        text("HI ${world.highScore}  ·  R${world.roundNo}", W / 2f, 130f * u, 16f * u, withA(Palette.WHITE, 150))

        // combo / kills / casco
        val rx = W - 130f * u
        text("x${world.combo}", rx, 66f * u, 60f * u, Palette.MAGENTA)
        text("KILLS ${world.kills}", rx, 96f * u, 18f * u, Palette.WHITE)
        fillC(withA(Palette.WHITE, 30))
        rect(W - 230f * u, 118f * u, W - 30f * u, 132f * u)
        fillC(healthColor(pl.hull / 100f))
        rect(W - 230f * u, 118f * u, W - 230f * u + 200f * u * (pl.hull / 100f), 132f * u)
        text("CASCO ${pl.hull.toInt()}%", rx, 150f * u, 14f * u, withA(Palette.WHITE, 170))

        // diagrama de zonas (frente = derecha)
        val dx = W * 0.5f
        val dy = H - 110f * u
        val len = 46f * u
        val hh = 22f * u
        strokeC(withA(Palette.CYAN, 120), 2f * u)
        rect(dx - len / 2, dy - hh / 2, dx + len / 2, dy + hh / 2)
        val zb = 5f * u
        fun zoneBar(zone: Zone, l: Float, t2: Float, r2: Float, b2: Float) {
            fillC(
                if (pl.detached[zone.ordinal]) withA(Palette.WHITE, 30)
                else healthColor(pl.zones[zone.ordinal] / 100f)
            )
            rect(l, t2, r2, b2)
        }
        zoneBar(Zone.FRONT, dx + len / 2 + 4f * u, dy - hh / 2, dx + len / 2 + 4f * u + zb, dy + hh / 2)
        zoneBar(Zone.REAR, dx - len / 2 - 4f * u - zb, dy - hh / 2, dx - len / 2 - 4f * u, dy + hh / 2)
        zoneBar(Zone.LEFT, dx - len / 2, dy - hh / 2 - 4f * u - zb, dx + len / 2, dy - hh / 2 - 4f * u)
        zoneBar(Zone.RIGHT, dx - len / 2, dy + hh / 2 + 4f * u, dx + len / 2, dy + hh / 2 + 4f * u + zb)

        // banner
        if (world.bannerT > 0f || world.roundOver) {
            val a = (min(1f, world.bannerT) * 255f).toInt()
            val col = when {
                world.roundOver -> Palette.RED
                world.banner.startsWith("KILL") -> Palette.YELLOW
                else -> Palette.CYAN
            }
            text(world.banner, W / 2f, H * 0.36f, 60f * u, withA(col, a))
            if (world.roundOver) {
                text(
                    "SCORE ${world.score}  ·  KILLS ${world.kills}",
                    W / 2f, H * 0.36f + 40f * u, 24f * u, withA(Palette.WHITE, a)
                )
            }
        }
    }

    private fun speedColor(f: Float): Int = when {
        f < 0.6f -> Palette.CYAN
        f < 0.85f -> Palette.YELLOW
        else -> Palette.RED
    }
}
