package cl.nexus.wreck

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * Simulación: rigid bodies OBB con SAT, impulsos con momento angular,
 * fricción de contacto, círculo de fricción en neumáticos, bicicleta para
 * la dirección, daño por energía de impacto y peatones por velocidad de cierre.
 * Sin dependencias de Android.
 */
class World(val size: Float = 2000f) {

    companion object {
        const val ROUND_TIME = 90f
        const val AI_COUNT = 6
        const val PED_MAX = 35
        const val PICKUP_MAX = 4
        const val PED_R = 7f
        const val SUBSTEPS = 4

        // Motor y frenos (u/s²). 1 g ≈ 98 u/s²
        const val ENGINE_ACC = 120f
        const val BRAKE_ACC = 130f
        const val MAX_SPEED = 560f
        const val NITRO_SPEED = 780f
        const val REVERSE_MAX = 200f
        const val DRAG = 0.000026f           // resistencia aerodinámica: a = DRAG·v²
        const val ROLL = 0.03f               // rodadura: a = ROLL·v

        // Neumáticos
        const val LAT_MAX = 105f             // agarre lateral máximo (~1.07 g)
        const val LONG_MAX = 105f
        const val GRIP_RATE = 12f
        const val WHEELBASE = 30f
        const val MAX_STEER = 0.62f
        const val MU_CONTACT = 0.35f

        // Contactos
        const val REST_CAR = 0.2f
        const val REST_WALL = 0.35f
        const val IMPACT_MIN = 90f           // cierre que empieza a dañar (~32 km/h)
        const val DMG_K = 0.0003f            // daño ∝ (v² − v_min²): energía del impacto

        // Peatones (velocidad de cierre)
        const val PED_STUN = 35f             // ~12 km/h: derriba
        const val PED_KILL = 100f            // ~36 km/h: mata
    }

    private class Hit(val n: V, val p: V, val impact: Float)

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

    /** Reinicia la ronda actual (tecla R en escritorio). */
    fun restart() {
        roundNo++
        newRound()
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

        updateSkids()
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
                if (!a.alive) break
                if (!b.alive) continue
                val hit = resolveBodies(a, b) ?: continue
                onHit(a, b, hit)
            }
            for (bx in boxes) {
                if (!a.alive) break
                val hit = resolveBodies(a, bx) ?: continue
                onHit(a, bx, hit)
            }
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
        val diff = angleDiff(c.ang, atan2(d.y, d.x))
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
        val r = V(-f.y, f.x)
        val vf0 = c.vel.dot(f)
        val power = c.powerFactor()

        if (c.nitroOn) c.nitro = max(0f, c.nitro - 30f * h)
        else c.nitro = min(100f, c.nitro + 6f * h)
        if (c.nitro <= 0f) c.nitroOn = false

        // Motor con curva de potencia (cae a 0 cerca de la velocidad máxima) y frenos
        var longAcc = 0f
        if (c.throttle > 0f) {
            val top = if (c.nitroOn) NITRO_SPEED else MAX_SPEED
            val vp = vf0.coerceAtLeast(0f) / top
            val curve = (1f - vp * vp).coerceIn(0f, 1f)
            val boost = if (c.nitroOn) 1.6f else 1f
            longAcc = ENGINE_ACC * boost * power * c.throttle * curve
        } else if (c.throttle < 0f) {
            longAcc = when {
                vf0 > 15f -> -BRAKE_ACC * -c.throttle
                vf0 > -REVERSE_MAX -> -ENGINE_ACC * 0.5f * -c.throttle
                else -> 0f
            }
        }
        // Resistencias: aire (cuadrática) y rodadura
        longAcc -= DRAG * vf0 * abs(vf0) + ROLL * vf0

        // Círculo de fricción: el agarre lateral disponible cae con la carga longitudinal
        val lr = (longAcc / LONG_MAX).coerceIn(-1f, 1f)
        var latMax = LAT_MAX * sqrt(1f - lr * lr)
        if (c.detached[Zone.REAR.ordinal]) latMax *= 0.6f
        val vl0 = c.vel.dot(r)
        val latAcc = (-vl0 * GRIP_RATE).coerceIn(-latMax, latMax)

        c.vel = c.vel + f * (longAcc * h) + r * (latAcc * h)

        // Dirección: el ángulo de rueda se reduce con la velocidad (estabilidad)
        val speed = c.vel.len()
        c.wheelAngle = c.steer * MAX_STEER / (1f + (speed / 150f) * (speed / 150f))

        // Yaw por bicicleta, limitado por la aceleración lateral máxima del neumático
        val vf = c.vel.dot(f)
        var wTarget = vf * tan(c.wheelAngle) / WHEELBASE
        val yawLimit = latMax * 1.1f / max(speed, 20f)
        wTarget = wTarget.coerceIn(-yawLimit, yawLimit)
        c.omega += (wTarget - c.omega) * min(1f, 10f * h)
        c.omega = c.omega.coerceIn(-9f, 9f)
        c.ang += c.omega * h

        c.pos = c.pos + c.vel * h
        c.latSpeed = abs(c.vel.dot(V(-sin(c.ang), cos(c.ang))))
    }

    private fun updateSkids() {
        for (c in cars) {
            if (!c.alive || c.latSpeed < 60f) continue
            if (Random.nextFloat() > 0.35f) continue
            val f = c.forward()
            fx += Fx(c.pos - f * 20f, V(0f, 0f), FxKind.SKID, 3f, 4f, Palette.BODY)
        }
    }

    // ---------------------------------------------------------------- geometría de contacto

    private fun axes(b: Body): Pair<V, V> {
        val u = V(cos(b.ang), sin(b.ang))
        return Pair(u, V(-u.y, u.x))
    }

    private fun support(b: Body, n: V): Float {
        val (u, v) = axes(b)
        return abs(n.dot(u)) * b.hx + abs(n.dot(v)) * b.hy
    }

    private fun corners(b: Body): List<V> {
        val (u, v) = axes(b)
        return listOf(
            b.pos + u * b.hx + v * b.hy,
            b.pos + u * b.hx - v * b.hy,
            b.pos - u * b.hx + v * b.hy,
            b.pos - u * b.hx - v * b.hy,
        )
    }

    private fun velAt(b: Body, r: V): V = b.vel + V(-r.y, r.x) * b.omega

    private fun applyImpulse(b: Body, imp: V, r: V) {
        if (b.invM == 0f) return
        b.vel = b.vel + imp * b.invM
        b.omega += r.cross(imp) * b.invI
    }

    /** SAT entre dos OBB + impulso con fricción. Devuelve null si no hay solape. */
    private fun resolveBodies(a: Body, b: Body): Hit? {
        if (a.invM == 0f && b.invM == 0f) return null
        val (ua, va) = axes(a)
        val (ub, vb) = axes(b)
        val candidates = listOf(ua, va, ub, vb)
        val d = b.pos - a.pos

        var minOv = Float.MAX_VALUE
        var n = V(0f, 0f)
        for (ax in candidates) {
            val dist = abs(d.dot(ax))
            val ov = support(a, ax) + support(b, ax) - dist
            if (ov <= 0f) return null
            if (ov < minOv) {
                minOv = ov
                n = if (d.dot(ax) >= 0f) ax else ax * -1f
            }
        }

        // Punto de contacto: el vértice que más penetra
        val aFace = a.pos.dot(n) + support(a, n)
        val bFace = b.pos.dot(n) - support(b, n)
        var bestDep = -Float.MAX_VALUE
        var contact = b.pos
        for (q in corners(b)) {
            val dep = aFace - q.dot(n)
            if (dep > bestDep) { bestDep = dep; contact = q }
        }
        for (p in corners(a)) {
            val dep = p.dot(n) - bFace
            if (dep > bestDep) { bestDep = dep; contact = p }
        }

        // Corrección de posición según masas inversas
        val inv = a.invM + b.invM
        if (inv > 0f) {
            a.pos = a.pos - n * (minOv * 0.85f * a.invM / inv)
            b.pos = b.pos + n * (minOv * 0.85f * b.invM / inv)
        }

        val ra = contact - a.pos
        val rb = contact - b.pos
        val vn = (velAt(b, rb) - velAt(a, ra)).dot(n)
        if (vn >= 0f) return Hit(n, contact, 0f)

        val e = if (a.invM == 0f || b.invM == 0f) REST_WALL else REST_CAR
        val kN = inv + ra.cross(n).let { it * it } * a.invI + rb.cross(n).let { it * it } * b.invI
        val jn = -(1f + e) * vn / kN
        applyImpulse(a, n * -jn, ra)
        applyImpulse(b, n * jn, rb)

        // Fricción de Coulomb en la tangente
        val t = V(-n.y, n.x)
        val vt = (velAt(b, rb) - velAt(a, ra)).dot(t)
        val kT = inv + ra.cross(t).let { it * it } * a.invI + rb.cross(t).let { it * it } * b.invI
        val maxF = MU_CONTACT * jn
        val jt = (-vt / kT).coerceIn(-maxF, maxF)
        applyImpulse(a, t * -jt, ra)
        applyImpulse(b, t * jt, rb)

        return Hit(n, contact, -vn)
    }

    private fun onHit(a: Car, b: Body, hit: Hit) {
        if (hit.impact <= IMPACT_MIN) return
        val dmg = (hit.impact * hit.impact - IMPACT_MIN * IMPACT_MIN) * DMG_K
        damage(a, hit.p, dmg, if (b is Car) b else null)
        if (b is Car) damage(b, hit.p, dmg, a)
        burst(hit.p, 6 + (hit.impact / 40f).toInt(), hit.impact * 0.6f, FxKind.SPARK, 0.35f, 2.5f, Palette.YELLOW)
        shake = min(shake + hit.impact / 400f, 2.5f)
    }

    private fun carPeds(c: Car) {
        for (p in peds) {
            if (!p.alive) continue
            val local = (p.pos - c.pos).rot(-c.ang)
            val cl = V(local.x.coerceIn(-c.hx, c.hx), local.y.coerceIn(-c.hy, c.hy))
            val closest = c.pos + cl.rot(c.ang)
            val d = p.pos - closest
            val dist = d.len()
            if (dist >= PED_R) continue
            val n = if (dist > 1e-3f) d * (1f / dist) else (p.pos - c.pos).norm()
            p.pos = closest + n * PED_R

            val carV = velAt(c, closest - c.pos)
            val closing = (carV - p.vel).dot(n)
            if (closing < 8f) continue

            if (closing >= PED_KILL) {
                p.alive = false
                p.timer = 2.5f
                p.vel = carV * 0.9f + n * (closing * 0.9f)
                burst(p.pos, 14, 150f, FxKind.BLOOD, 0.9f, 3f, Palette.BLOOD)
                if (c.isPlayer) {
                    score += 50 * combo
                    bumpCombo()
                }
            } else if (closing >= PED_STUN) {
                p.stunT = 1.5f
                p.vel = n * (closing * 1.0f) + carV * 0.3f
            }
        }
    }

    /** Daño por zona según el punto de contacto; el desborde va al casco. */
    private fun damage(c: Car, from: V, amount: Float, by: Car?) {
        if (!c.alive || amount < 0.5f) return
        val l = (from - c.pos).rot(-c.ang)
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
        burst(c.pos + off.rot(c.ang), 10, 170f, FxKind.DEBRIS, 1.6f, 6f, Palette.YELLOW)
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
            if (p.stunT > 0f) {
                p.stunT -= dt
                p.vel = p.vel * max(0f, 1f - 3f * dt)
                p.pos = pushOutOfBoxes(p.pos + p.vel * dt, PED_R)
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
