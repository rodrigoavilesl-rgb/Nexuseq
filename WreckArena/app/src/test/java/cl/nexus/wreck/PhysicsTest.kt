package cl.nexus.wreck

import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsTest {

    private fun idle() = Input()

    /** Mundo sin obstáculos en la línea recta de prueba. */
    private fun world(): World {
        val w = World()
        w.boxes.clear()
        w.peds.clear()
        w.cars.removeAll { !it.isPlayer }
        return w
    }

    @Test
    fun accelerationReachesRealisticTopSpeed() {
        val w = world()
        val p = w.player
        p.pos = V(1000f, 1000f); p.vel = V(0f, 0f); p.ang = 0f
        val input = Input(gas = true)
        var t = 0f
        while (t < 30f) { w.update(1f / 60f, input); t += 1f / 60f }
        val kmh = p.vel.len() * 0.36f
        assertTrue("velocidad tras 30 s = $kmh km/h", kmh in 150f..215f)
    }

    @Test
    fun frontalWallImpactDamagesFrontZone() {
        val w = world()
        w.boxes += Box(1100f, 900f, 60f, 200f)
        val p = w.player
        p.pos = V(1000f, 1000f); p.vel = V(400f, 0f); p.ang = 0f
        repeat(120) { w.update(1f / 60f, idle()) }
        assertTrue("zona frontal = ${p.zones[Zone.FRONT.ordinal]}", p.zones[Zone.FRONT.ordinal] < 100f)
        assertTrue("casco = ${p.hull}", p.hull < 100f)
    }

    @Test
    fun lowSpeedBumpDoesNoDamage() {
        val w = world()
        w.boxes += Box(1100f, 900f, 60f, 200f)
        val p = w.player
        p.pos = V(1000f, 1000f); p.vel = V(60f, 0f); p.ang = 0f
        repeat(120) { w.update(1f / 60f, idle()) }
        assertTrue("casco = ${p.hull}", p.hull == 100f)
    }

    @Test
    fun pedestrianKilledAboveLethalSpeedOnly() {
        val w = world()
        val p = w.player
        p.pos = V(1000f, 1000f); p.vel = V(130f, 0f); p.ang = 0f
        w.peds.clear()
        val ped = Ped(V(1032f, 1000f), 0)
        w.peds += ped
        w.update(1f / 60f, idle())
        assertTrue("peatón debería morir a 130 u/s", !ped.alive)

        val w2 = world()
        val p2 = w2.player
        p2.pos = V(1000f, 1000f); p2.vel = V(40f, 0f); p2.ang = 0f
        w2.peds.clear()
        val ped2 = Ped(V(1032f, 1000f), 0)
        w2.peds += ped2
        w2.update(1f / 60f, idle())
        assertTrue("peatón debería sobrevivir a 40 u/s", ped2.alive)
    }
}
