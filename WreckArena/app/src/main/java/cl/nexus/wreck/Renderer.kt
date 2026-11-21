package cl.nexus.wreck

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Dibujo del mundo y HUD tipo telemetría. Corre en el hilo del loop. */
class Renderer {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private var camX = 0f
    private var camY = 0f
    private var camReady = false

    fun draw(c: Canvas, w: Int, h: Int, world: World, input: Input) {
        val W = w.toFloat()
        val H = h.toFloat()
        val s = min(H / 700f, W / 1000f)          // escala mundo → pantalla
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

        c.drawColor(Palette.BG)

        // grilla
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Palette.GRID
        val step = 100f
        val halfW = W / 2f / s
        val halfH = H / 2f / s
        var gx = floor((camX - halfW) / step) * step
        while (gx <= camX + halfW) {
            c.drawLine(sx(gx), 0f, sx(gx), H, p); gx += step
        }
        var gy = floor((camY - halfH) / step) * step
        while (gy <= camY + halfH) {
            c.drawLine(0f, sy(gy), W, sy(gy), p); gy += step
        }

        // muros / edificios con neón
        for (b in world.boxes) {
            val x0 = sx(b.x); val y0 = sy(b.y)
            val x1 = sx(b.x + b.w); val y1 = sy(b.y + b.h)
            p.style = Paint.Style.FILL
            p.color = Palette.WALL_FILL
            c.drawRect(x0, y0, x1, y1, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f
            p.color = withA(Palette.MAGENTA, 60)
            c.drawRect(x0, y0, x1, y1, p)
            p.strokeWidth = 2f
            p.color = Palette.MAGENTA
            c.drawRect(x0, y0, x1, y1, p)
        }

        // pickups pulsantes
        val pulse = 1f + 0.15f * sin(world.time * 6f)
        for (pk in world.pickups) {
            val x = sx(pk.pos.x); val y = sy(pk.pos.y)
            val col = if (pk.type == PickupType.REPAIR) Palette.LIME else Palette.ORANGE
            p.style = Paint.Style.FILL
            p.color = withA(col, 70)
            c.drawCircle(x, y, 22f * s * pulse, p)
            p.color = col
            c.drawCircle(x, y, 10f * s, p)
            tp.color = Palette.BG
            tp.textSize = 14f * s
            c.drawText(if (pk.type == PickupType.REPAIR) "+" else "N", x, y + 5f * s, tp)
        }

        // peatones
        for (pd in world.peds) {
            p.style = Paint.Style.FILL
            p.color = if (pd.alive) pd.color
            else withA(Palette.BLOOD, (255f * (pd.timer / 2.5f)).coerceIn(0f, 255f).toInt())
            c.drawCircle(sx(pd.pos.x), sy(pd.pos.y), World.PED_R * s, p)
        }

        // autos
        for (car in world.cars) drawCar(c, car, sx(car.pos.x), sy(car.pos.y), s)

        // partículas
        for (f in world.fx) {
            val k = (f.t / f.life).coerceIn(0f, 1f)
            val x = sx(f.pos.x); val y = sy(f.pos.y)
            p.style = Paint.Style.FILL
            when (f.kind) {
                FxKind.SMOKE -> {
                    p.color = withA(f.color, (60f * k).toInt())
                    c.drawCircle(x, y, f.size * s * (1.6f - k), p)
                }
                FxKind.SPARK -> {
                    p.color = withA(f.color, (255f * k).toInt())
                    c.drawCircle(x, y, f.size * s * k + 1f, p)
                }
                FxKind.BLOOD -> {
                    p.color = withA(Palette.BLOOD, (220f * k).toInt())
                    c.drawCircle(x, y, f.size * s, p)
                }
                FxKind.SKID -> {
                    p.color = withA(Palette.BODY, (110f * k).toInt())
                    c.drawCircle(x, y, f.size * s, p)
                }
                FxKind.DEBRIS -> {
                    val hs = f.size * s / 2f
                    p.color = withA(f.color, (255f * k).toInt())
                    c.drawRect(x - hs, y - hs, x + hs, y + hs, p)
                }
            }
        }

        drawHud(c, W, H, world, input)
    }

    private fun drawCar(c: Canvas, car: Car, x: Float, y: Float, s: Float) {
        val hl = 26f * s
        val hw = 13f * s
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(car.ang.toDouble()).toFloat())

        if (!car.alive) {
            p.style = Paint.Style.FILL
            p.color = Palette.WRECK
            c.drawRoundRect(-hl, -hw, hl, hw, 6f * s, 6f * s, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * s
            p.color = withA(Palette.ORANGE, 110)
            c.drawRoundRect(-hl, -hw, hl, hw, 6f * s, 6f * s, p)
            c.restore()
            return
        }

        // halo
        p.style = Paint.Style.FILL
        p.color = withA(car.color, if (car.flash > 0f) 230 else 60)
        c.drawRoundRect(-hl - 3f * s, -hw - 3f * s, hl + 3f * s, hw + 3f * s, 9f * s, 9f * s, p)

        // cuerpo
        p.color = if (car.flash > 0f) Palette.WHITE else Palette.BODY
        c.drawRoundRect(-hl, -hw, hl, hw, 7f * s, 7f * s, p)

        // zonas de daño: color según salud, sin dibujar si el panel se desprendió
        val t = 5f * s
        for (i in 0 until 4) {
            if (car.detached[i]) continue
            p.color = withA(healthColor(car.zones[i] / 100f), 230)
            when (i) {
                Zone.FRONT.ordinal -> c.drawRect(hl - t, -hw + 2f * s, hl - s, hw - 2f * s, p)
                Zone.REAR.ordinal -> c.drawRect(-hl + s, -hw + 2f * s, -hl + t, hw - 2f * s, p)
                Zone.LEFT.ordinal -> c.drawRect(-hl + 4f * s, -hw + s, hl - 4f * s, -hw + t * 0.6f, p)
                else -> c.drawRect(-hl + 4f * s, hw - t * 0.6f, hl - 4f * s, hw - s, p)
            }
        }

        // parabrisas
        p.style = Paint.Style.FILL
        p.color = withA(Palette.CYAN, 90)
        c.drawRect(hl * 0.12f, -hw * 0.65f, hl * 0.45f, hw * 0.65f, p)

        // contorno neón
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * s
        p.color = car.color
        c.drawRoundRect(-hl, -hw, hl, hw, 7f * s, 7f * s, p)

        // ruedas: las delanteras giran con la dirección real
        p.style = Paint.Style.FILL
        p.color = Palette.WHEEL
        drawWheel(c, hl * 0.62f, -hw - 1.5f * s, car.wheelAngle, s)
        drawWheel(c, hl * 0.62f, hw + 1.5f * s, car.wheelAngle, s)
        drawWheel(c, -hl * 0.62f, -hw - 1.5f * s, 0f, s)
        drawWheel(c, -hl * 0.62f, hw + 1.5f * s, 0f, s)

        // faros y llama de nitro
        p.style = Paint.Style.FILL
        p.color = Palette.YELLOW
        c.drawCircle(hl - 2f * s, -hw * 0.55f, 1.8f * s, p)
        c.drawCircle(hl - 2f * s, hw * 0.55f, 1.8f * s, p)
        if (car.nitroOn) {
            p.color = Palette.ORANGE
            c.drawRect(-hl - 9f * s, -hw * 0.4f, -hl - s, hw * 0.4f, p)
        }
        c.restore()
    }

    private fun drawWheel(c: Canvas, x: Float, y: Float, angle: Float, s: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        c.drawRect(-5f * s, -2.5f * s, 5f * s, 2.5f * s, p)
        c.restore()
    }

    private fun drawHud(c: Canvas, W: Float, H: Float, world: World, input: Input) {
        val u = min(W / 1600f, H / 900f)
        val pl = world.player

        // velocímetro tipo smartwatch
        val cx = 140f * u
        val cy = 140f * u
        val r = 96f * u
        val kmh = pl.vel.len() * 0.36f
        val frac = (kmh / 220f).coerceIn(0f, 1f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 12f * u
        p.color = withA(Palette.CYAN, 40)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, 270f, false, p)
        p.color = speedColor(frac)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 135f, 270f * frac, false, p)
        tp.color = Palette.WHITE
        tp.textSize = 52f * u
        c.drawText("${kmh.toInt()}", cx, cy + 12f * u, tp)
        tp.color = Palette.CYAN
        tp.textSize = 14f * u
        c.drawText("KM/H", cx, cy + 40f * u, tp)

        // nitro
        p.style = Paint.Style.FILL
        p.color = withA(Palette.WHITE, 30)
        c.drawRect(40f * u, 270f * u, 240f * u, 282f * u, p)
        p.color = Palette.ORANGE
        c.drawRect(40f * u, 270f * u, 40f * u + 200f * u * (pl.nitro / 100f), 282f * u, p)
        tp.color = Palette.ORANGE
        tp.textSize = 14f * u
        c.drawText("NOS", 140f * u, 262f * u, tp)

        // tiempo / score
        val t = world.timeLeft.coerceAtLeast(0f).toInt()
        tp.textSize = 58f * u
        tp.color = if (t <= 10) Palette.RED else Palette.CYAN
        c.drawText("%d:%02d".format(t / 60, t % 60), W / 2f, 66f * u, tp)
        tp.textSize = 24f * u
        tp.color = Palette.WHITE
        c.drawText("SCORE ${world.score}", W / 2f, 104f * u, tp)
        tp.textSize = 16f * u
        tp.color = withA(Palette.WHITE, 150)
        c.drawText("HI ${world.highScore}  ·  R${world.roundNo}", W / 2f, 130f * u, tp)

        // combo / kills / casco
        val rx = W - 130f * u
        tp.textSize = 60f * u
        tp.color = Palette.MAGENTA
        c.drawText("x${world.combo}", rx, 66f * u, tp)
        tp.textSize = 18f * u
        tp.color = Palette.WHITE
        c.drawText("KILLS ${world.kills}", rx, 96f * u, tp)
        p.style = Paint.Style.FILL
        p.color = withA(Palette.WHITE, 30)
        c.drawRect(W - 230f * u, 118f * u, W - 30f * u, 132f * u, p)
        p.color = healthColor(pl.hull / 100f)
        c.drawRect(W - 230f * u, 118f * u, W - 230f * u + 200f * u * (pl.hull / 100f), 132f * u, p)
        tp.textSize = 14f * u
        tp.color = withA(Palette.WHITE, 170)
        c.drawText("CASCO ${pl.hull.toInt()}%", rx, 150f * u, tp)

        // diagrama de zonas (mini auto, frente = derecha)
        val dx = W * 0.5f
        val dy = H - 110f * u
        val L = 46f * u
        val hh = 22f * u
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * u
        p.color = withA(Palette.CYAN, 120)
        c.drawRect(dx - L / 2, dy - hh / 2, dx + L / 2, dy + hh / 2, p)
        val zb = 5f * u
        fun zoneBar(zone: Zone, l: Float, t2: Float, r2: Float, b2: Float) {
            p.style = Paint.Style.FILL
            p.color = if (pl.detached[zone.ordinal]) withA(Palette.WHITE, 30)
            else healthColor(pl.zones[zone.ordinal] / 100f)
            c.drawRect(l, t2, r2, b2, p)
        }
        zoneBar(Zone.FRONT, dx + L / 2 + 4f * u, dy - hh / 2, dx + L / 2 + 4f * u + zb, dy + hh / 2)
        zoneBar(Zone.REAR, dx - L / 2 - 4f * u - zb, dy - hh / 2, dx - L / 2 - 4f * u, dy + hh / 2)
        zoneBar(Zone.LEFT, dx - L / 2, dy - hh / 2 - 4f * u - zb, dx + L / 2, dy - hh / 2 - 4f * u)
        zoneBar(Zone.RIGHT, dx - L / 2, dy + hh / 2 + 4f * u, dx + L / 2, dy + hh / 2 + 4f * u + zb)

        // controles táctiles
        val br = Layout.btnR(W, H)
        drawButton(c, Layout.gas(W, H), "GAS", Palette.LIME, input.gas, br, u)
        drawButton(c, Layout.brake(W, H), "BRK", Palette.RED, input.brake, br, u)
        drawButton(c, Layout.nitro(W, H), "NOS", Palette.ORANGE, input.nitro, br * 0.85f, u)

        // barra de dirección
        val sy = H - 60f * u
        val sc = Layout.steerCenter(W)
        val sh = Layout.steerHalf(W)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f * u
        p.color = withA(Palette.WHITE, 50)
        c.drawLine(sc - sh, sy, sc + sh, sy, p)
        p.style = Paint.Style.FILL
        p.color = Palette.CYAN
        c.drawCircle(sc + input.steer * sh, sy, 12f * u, p)

        // banner
        if (world.bannerT > 0f || world.roundOver) {
            val a = (min(1f, world.bannerT) * 255f).toInt()
            val col = when {
                world.roundOver -> Palette.RED
                world.banner.startsWith("KILL") -> Palette.YELLOW
                else -> Palette.CYAN
            }
            tp.textSize = 60f * u
            tp.color = withA(col, a)
            c.drawText(world.banner, W / 2f, H * 0.36f, tp)
            if (world.roundOver) {
                tp.textSize = 24f * u
                tp.color = withA(Palette.WHITE, a)
                c.drawText("SCORE ${world.score}  ·  KILLS ${world.kills}", W / 2f, H * 0.36f + 40f * u, tp)
            }
        }
    }

    private fun drawButton(c: Canvas, center: V, label: String, col: Int, active: Boolean, r: Float, u: Float) {
        p.style = Paint.Style.FILL
        p.color = withA(col, if (active) 150 else 35)
        c.drawCircle(center.x, center.y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * u
        p.color = withA(col, 220)
        c.drawCircle(center.x, center.y, r, p)
        tp.textSize = 26f * u
        tp.color = Palette.WHITE
        c.drawText(label, center.x, center.y + 9f * u, tp)
    }

    private fun speedColor(f: Float): Int = when {
        f < 0.6f -> Palette.CYAN
        f < 0.85f -> Palette.YELLOW
        else -> Palette.RED
    }
}
