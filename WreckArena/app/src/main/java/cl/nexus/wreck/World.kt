package cl.nexus.wreck

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulación completa: física 2D por circunferencias, daño por zonas,
 * peatones, pickups, IA y reglas de ronda. Sin dependencias de Android.
 */
class World(val size: Float = 2000f) {

    companion object {
        const val ROUND_TIME = 90f
        const val AI_COUNT = 6
        const val PED_MAX = 35
        const val PICKUP_MAX = 4
        const val PED_R = 7f
        const val REST = 0.3f
        const val ENGINE_ACC = 720f
        const val BRAKE_ACC = 1300f
        const val MAX_SPEED = 560f
        const val NITRO_SPEED = 780f
        const val REVERSE_MAX = 200f
        const val SUBSTEPS = 3
    }

    val cars = ArrayList<Car>()
    val peds = ArrayList<Ped>()
    val boxes = ArrayList<Box>()
    val pickups = ArrayList<Pickup>()
    val fx = ArrayList<Fx>()

    var time = 0f
    var timeLeft = ROUND_TIME
    var score = 0
    var highScore = 0
    var kills = 0
    var combo = 1
    var shake = 0f
    var roundOver = false
    var roundNo = 1
    var banner = ""
    var bannerT = 0f

    private var comboT = 0f
    private var slowT = 0f
    private var restartT = 0f
    private var pickupT = 0f
    private var pedT = 0f
    private var aiSpawnT = 0f
    private var nextId = 1
    private val center = V(size / 2f, size / 2f)

    val player: Car get() = cars[0]

    init {
        newRound()
    }

    // ---------------------------------------------------------------- rondas

    private fun newRound() {
        cars.clear(); peds.clear(); pickups.clear(); fx.clear()
        buildMap()
        timeLeft = ROUND_TIME
        score = 0; kills = 0; combo = 1; comboT = 0f
        roundOver = false; shake = 0f; slowT = 0f
        cars += Car(0, true, Palette.PLAYER, center, 0f)
        repeat(AI_COUNT) { spawnAi() }
        repeat(PED_MAX) { spawnPed() }
        repeat(PICKUP_MAX) { spawnPickup() }
        banner = "ROUND $roundNo"
        bannerT = 2f
    }

    private fun endRound(msg: String) {
        if (roundOver) return
        roundOver = true
        restartT = 3.2f
        highScore = max(highScore, score)
        banner = msg
        bannerT = 3.2f
        slowT = 0f
    }

    private fun buildMap() {
        boxes.clear()
        val t = 60f
        boxes += Box(0f, 0f, size, t)
        boxes += Box(0f, size - t, size, t)
        boxes += Box(0f, 0f, t, size)
        boxes += Box(size - t, 0f, t, size)
        val cell = 250f
        for (gx in 0 until 7) {
            for (gy in 0 until 7) {
                if (Random.nextFloat() > 0.55f) continue
                val w = 90f + Random.nextFloat() * 90f
                val h = 90f + Random.nextFloat() * 90f
                val x = 150f + gx * cell + Random.nextFloat() * (cell - w)
                val y = 150f + gy * cell + Random.nextFloat() * (cell - h)
                val box = Box(x, y, w, h)
                if (distToBox(center, box) < 240f) continue   // zona de salida libre
                boxes += box
            }
        }
    }

    private fun freeSpot(r: Float): V? {
        repeat(60) {
            val p = V(
                Random.nextFloat() * (size - 300f) + 150f,
                Random.nextFloat() * (size - 300f) + 150f,
            )
            if (boxes.none { distToBox(p, it) < r + 20f } &&
                cars.none { (it.pos - p).len() < 220f }
            ) return p
        }
        return null
    }

    private fun spawnAi() {
        val p = freeSpot(28f) ?: return
        val col = Palette.AI[Random.nextInt(Palette.AI.size)]
        cars += Car(nextId++, false, col, p, Random.nextFloat() * TAU)
    }

    private fun spawnPed() {
        val p = freeSpot(PED_R) ?: return
        peds += Ped(p, Palette.PEDS[Random.nextInt(Palette.PEDS.size)])
    }

    private fun spawnPickup() {
        val p = freeSpot(20f) ?: return
        val type = if (Random.nextBoolean()) PickupType.REPAIR else PickupType.NITRO
        pickups += Pickup(p, type)
    }

    // ---------------------------------------------------------------- update

    fun update(dtReal: Float, input: Input) {
        if (slowT > 0f) slowT -= dtReal
        if (bannerT > 0f) bannerT -= dtReal
        shake = max(0f, shake - dtReal * 5f)

        if (roundOver) {
            restartT -= dtReal
            updateFx(dtReal)
            if (restartT <= 0f) {
                roundNo++
                newRound()
            }
            return
        }

        val dt = if (slowT > 0f) dtReal * 0.35f else dtReal
        time += dt
        timeLeft -= dt
        if (timeLeft <= 0f) {
            timeLeft = 0f
            endRound("TIME UP")
            return
        }
        if (comboT > 0f) {
            comboT -= dt
            if (comboT <= 0f) combo = 1
        }

        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) { if (!roundOver) physicsStep(h, input) }

        updatePeds(dt)
        updatePickups(dt)
        updateFx(dt)
        maintainCars(dt)

        pickupT += dt
        if (pickupT > 7f && pickups.size < PICKUP_MAX) {
            pickupT = 0f
            spawnPickup()
        }
        pedT += dt
        if (pedT > 1.2f && peds.size < PED_MAX) {
            pedT = 0f
            spawnPed()
        }
    }

    private fun maintainCars(dt: Float) {
        for (c in cars) if (!c.isPlayer && !c.alive) c.respawnT -= dt
        cars.removeAll { !it.isPlayer && !it.alive && it.respawnT <= 0f }
        aiSpawnT += dt
        val aiNow = cars.count { !it.isPlayer }
        if (aiNow < AI_COUNT && aiSpawnT > 2.5f) {
            aiSpawnT = 0f
            spawnAi()
        }
    }

    private fun physicsStep(h: Float, input: Input) {
        for (c in cars) {
            if (!c.alive) continue
            if (c.isPlayer) controlPlayer(c, input) else ai(c, h)
            drive(c, h)
        }
        for (i in cars.indices) {
            val a = cars[i]
            if (!a.alive) continue
            for (j in i + 1 until cars.size) {
                val b = cars[j]
                if (b.alive && a.alive) carCar(a, b)
            }
            for (bx in boxes) if (a.alive) carBox(a, bx)
            if (a.alive) carPeds(a)
        }
        for (c in cars) c.flash = max(0f, c.flash - h)
    }

    // ---------------------------------------------------------------- control

    private fun controlPlayer(c: Car, input: Input) {
        c.throttle = when {
            input.gas -> 1f
            input.brake -> -1f
            else -> 0f
        }
        c.steer = input.steer
        c.nitroOn = input.nitro && c.throttle > 0f && c.nitro > 0f
    }

    private fun ai(c: Car, h: Float) {
        var target: V? = null
        var best = Float.MAX_VALUE
        for (o in cars) {
            if (o === c || !o.alive) continue
            val d = (o.pos - c.pos).len()
            if (d < best) {
                best = d
                target = o.pos
            }
        }
        if (best > 700f) {
            var bestP = Float.MAX_VALUE
            for (p in peds) {
                if (!p.alive) continue
                val d = (p.pos - c.pos).len()
                if (d < bestP) {
                    bestP = d
                    target = p.pos
                }
            }
        }
        val t = target ?: run {
            c.throttle = 0f
            c.steer = 0f
            return
        }

        if (c.aiReverseT > 0f) {
            c.aiReverseT -= h
            c.throttle = -1f
            return
        }

        val d = t - c.pos
        val diff = angleDiff(c.heading, atan2(d.y, d.x))
        c.steer = (diff * 2.2f).coerceIn(-1f, 1f)
        c.throttle = if (abs(diff) > 2.4f) -0.6f else 0.95f

        if (c.vel.len() < 12f && c.throttle > 0f) c.aiStuckT += h else c.aiStuckT = 0f
        if (c.aiStuckT > 1.1f) {
            c.aiStuckT = 0f
            c.aiReverseT = 0.9f
            c.steer = if (Random.nextBoolean()) 1f else -1f
            c.throttle = -1f
        }
    }

    // ---------------------------------------------------------------- dinámica

    private fun drive(c: Car, h: Float) {
        val f = c.forward()
        val right = V(-f.y, f.x)
        val fwd = c.vel.dot(f)
        val power = c.powerFactor()

        if (c.nitroOn) c.nitro = max(0f, c.nitro - 30f * h)
        else c.nitro = min(100f, c.nitro + 6f * h)
        if (c.nitro <= 0f) c.nitroOn = false

        if (c.throttle > 0f) {
            val acc = ENGINE_ACC * power * c.throttle * (if (c.nitroOn) 1.7f else 1f)
            val vMax = (if (c.nitroOn) NITRO_SPEED else MAX_SPEED) * power
            if (fwd < vMax) c.vel = c.vel + f * (acc * h)
        } else if (c.throttle < 0f) {
            if (fwd > 15f) c.vel = c.vel - f * (BRAKE_ACC * h)
            else if (fwd > -REVERSE_MAX) c.vel = c.vel - f * (ENGINE_ACC * 0.55f * h)
        }

        // fricción lateral: agarre alto normal, bajo en derrape
        val lat = c.vel.dot(right)
        val drifting = abs(c.steer) > 0.6f && abs(fwd) > 220f
        var grip = if (drifting) 2.4f else 9f
        if (c.detached[Zone.REAR.ordinal]) grip *= 0.6f
        c.vel = c.vel - right * (lat * min(1f, grip * h))
        c.vel = c.vel * max(0f, 1f - 0.25f * h)

        // giro: depende de velocidad y se invierte en reversa
        val speedK = (abs(fwd) / 160f).coerceIn(0f, 1f)
        val dir = if (fwd < -5f) -1f else 1f
        val maxTurn = 2.9f * speedK * dir * (if (drifting) 1.15f else 1f)
        c.steerRate += (c.steer * maxTurn - c.steerRate) * min(1f, 10f * h)
        c.spin *= max(0f, 1f - 3f * h)
        c.heading += (c.steerRate + c.spin) * h
        c.pos = c.pos + c.vel * h
    }

    // ---------------------------------------------------------------- colisiones

    private fun carCar(a: Car, b: Car) {
        val d = b.pos - a.pos
        val dist = d.len()
        val minD = a.radius + b.radius
        if (dist >= minD || dist < 1e-3f) return
        val n = d * (1f / dist)
        val half = (minD - dist) * 0.5f
        a.pos = a.pos - n * half
        b.pos = b.pos + n * half

        val rv = (b.vel - a.vel).dot(n)
        if (rv >= 0f) return
        val j = -(1f + REST) * rv * 0.5f
        a.vel = a.vel - n * j
        b.vel = b.vel + n * j

        val speed = -rv
        val tv = (b.vel - a.vel).dot(V(-n.y, n.x))
        a.spin -= tv * 0.003f
        b.spin += tv * 0.003f

        if (speed > 120f) {
            val contact = a.pos + n * a.radius
            val dmg = (speed - 110f) * 0.13f
            damage(a, b.pos, dmg, b)
            damage(b, a.pos, dmg, a)
            burst(contact, 8, 220f, FxKind.SPARK, 0.35f, 2.5f, Palette.YELLOW)
            shake = min(shake + speed / 200f, 2.5f)
        }
    }

    private fun carBox(c: Car, b: Box) {
        val q = closestOnBox(c.pos, b)
        val d = c.pos - q
        val dist = d.len()
        if (dist >= c.radius) return
        val n = if (dist > 1e-3f) d * (1f / dist) else V(0f, -1f)
        c.pos = c.pos + n * (c.radius - dist)
        val vn = c.vel.dot(n)
        if (vn < 0f) {
            c.vel = c.vel - n * ((1f + 0.35f) * vn)
            val impact = -vn
            val dmg = max(0f, impact - 120f) * 0.13f
            // la pared está en dirección -n desde el auto
            damage(c, c.pos - n * 40f, dmg, null)
            if (impact > 140f) {
                burst(q, 4, 180f, FxKind.SPARK, 0.3f, 2f, Palette.YELLOW)
                shake = min(shake + impact / 250f, 2.5f)
            }
        }
    }

    private fun carPeds(c: Car) {
        val speed = c.vel.len()
        for (p in peds) {
            if (!p.alive) continue
            val d = p.pos - c.pos
            val dist = d.len()
            val minD = c.radius + PED_R
            if (dist >= minD || dist < 1e-3f) continue
            val n = d * (1f / dist)
            if (speed > 70f) {
                p.alive = false
                p.timer = 2.5f
                p.vel = c.vel * 0.9f + n * 80f
                burst(p.pos, 14, 150f, FxKind.BLOOD, 0.9f, 3f, Palette.BLOOD)
                if (c.isPlayer) {
                    score += 50 * combo
                    bumpCombo()
                }
            } else {
                p.pos = p.pos + n * (minD - dist)
            }
        }
    }

    /** Aplica daño por zona según la dirección del impacto; desborde va al casco. */
    private fun damage(c: Car, from: V, amount: Float, by: Car?) {
        if (!c.alive || amount < 0.5f) return
        val l = (from - c.pos).rot(-c.heading)
        val zi = if (abs(l.x) > abs(l.y)) {
            if (l.x > 0f) Zone.FRONT.ordinal else Zone.REAR.ordinal
        } else {
            if (l.y > 0f) Zone.RIGHT.ordinal else Zone.LEFT.ordinal
        }
        c.flash = 0.12f
        var hullDmg = amount * 0.45f
        if (c.detached[zi]) {
            hullDmg += amount * 0.6f
        } else {
            c.zones[zi] -= amount
            if (c.zones[zi] <= 0f) {
                hullDmg += -c.zones[zi] * 0.5f
                c.zones[zi] = 0f
                c.detached[zi] = true
                detachPanel(c, zi)
            }
        }
        c.hull = max(0f, c.hull - hullDmg)
        if (by?.isPlayer == true && !c.isPlayer) score += (amount * 2f).toInt()
        if (c.hull <= 0f) kill(c, by)
    }

    private fun kill(c: Car, by: Car?) {
        if (!c.alive) return
        c.alive = false
        c.respawnT = 4f
        c.nitroOn = false
        c.throttle = 0f
        explode(c.pos, c.color)
        shake = max(shake, 2.2f)
        if (c.isPlayer) {
            endRound("WRECKED")
            return
        }
        if (by?.isPlayer == true) {
            kills++
            val pts = 500 * combo
            score += pts
            bumpCombo()
            slowT = 0.45f
            banner = "KILL +$pts"
            bannerT = 1.4f
        }
    }

    private fun bumpCombo() {
        combo = min(combo + 1, 8)
        comboT = 4f
    }

    private fun detachPanel(c: Car, zi: Int) {
        val off = when (zi) {
            Zone.FRONT.ordinal -> V(26f, 0f)
            Zone.REAR.ordinal -> V(-26f, 0f)
            Zone.LEFT.ordinal -> V(0f, -13f)
            else -> V(0f, 13f)
        }
        burst(c.pos + off.rot(c.heading), 10, 170f, FxKind.DEBRIS, 1.6f, 6f, Palette.YELLOW)
        shake = max(shake, 1.2f)
    }

    // ---------------------------------------------------------------- entorno

    private fun updatePeds(dt: Float) {
        for (p in peds) {
            if (!p.alive) {
                p.timer -= dt
                p.vel = p.vel * max(0f, 1f - 4f * dt)
                p.pos = p.pos + p.vel * dt
                continue
            }
            p.wanderT -= dt
            if (p.wanderT <= 0f) {
                val a = Random.nextFloat() * TAU
                p.wander = V(cos(a), sin(a))
                p.wanderT = 1.2f + Random.nextFloat() * 1.8f
            }
            var flee = V(0f, 0f)
            for (c in cars) {
                if (!c.alive || c.vel.len() < 80f) continue
                val d = p.pos - c.pos
                val dist = d.len()
                if (dist < 170f) flee = flee + d.norm() * ((170f - dist) / 170f)
            }
            val desired = p.wander * 22f + flee * 160f
            p.vel = p.vel + (desired - p.vel) * min(1f, 4f * dt)
            p.pos = pushOutOfBoxes(p.pos + p.vel * dt, PED_R)
        }
        peds.removeAll { !it.alive && it.timer <= 0f }
    }

    private fun pushOutOfBoxes(p: V, r: Float): V {
        var out = p
        for (b in boxes) {
            val q = closestOnBox(out, b)
            val d = out - q
            val dist = d.len()
            if (dist < r) {
                val n = if (dist > 1e-3f) d * (1f / dist) else V(0f, -1f)
                out = q + n * r
            }
        }
        return out
    }

    private fun updatePickups(dt: Float) {
        val p = player
        val it = pickups.iterator()
        while (it.hasNext()) {
            val pk = it.next()
            pk.life -= dt
            if (p.alive && (pk.pos - p.pos).len() < 38f) {
                applyPickup(pk.type)
                it.remove()
            } else if (pk.life <= 0f) {
                it.remove()
            }
        }
    }

    private fun applyPickup(t: PickupType) {
        val p = player
        when (t) {
            PickupType.REPAIR -> {
                p.hull = min(100f, p.hull + 30f)
                for (i in 0 until 4) {
                    if (!p.detached[i]) p.zones[i] = min(100f, p.zones[i] + 40f)
                }
                banner = "REPAIR +30"
            }
            PickupType.NITRO -> {
                p.nitro = min(100f, p.nitro + 45f)
                banner = "NITRO +45"
            }
        }
        bannerT = 1f
        val col = if (t == PickupType.REPAIR) Palette.LIME else Palette.ORANGE
        burst(p.pos, 16, 160f, FxKind.SPARK, 0.5f, 3f, col)
    }

    // ---------------------------------------------------------------- fx

    private fun explode(p: V, col: Int) {
        burst(p, 30, 280f, FxKind.SPARK, 0.7f, 3f, Palette.ORANGE)
        burst(p, 12, 120f, FxKind.SMOKE, 1.6f, 10f, 0xFF555566.toInt())
        burst(p, 12, 200f, FxKind.DEBRIS, 1.3f, 5f, col)
    }

    private fun burst(p: V, n: Int, speed: Float, kind: FxKind, life: Float, size: Float, color: Int) {
        repeat(n) {
            val a = Random.nextFloat() * TAU
            val s = speed * (0.3f + Random.nextFloat() * 0.7f)
            fx += Fx(
                p,
                V(cos(a), sin(a)) * s,
                kind,
                life * (0.6f + Random.nextFloat() * 0.4f),
                size,
                color,
            )
        }
    }

    private fun updateFx(dt: Float) {
        val it = fx.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t -= dt
            if (f.t <= 0f) {
                it.remove()
                continue
            }
            f.pos = f.pos + f.vel * dt
            f.vel = f.vel * max(0f, 1f - 3f * dt)
        }
    }
}
