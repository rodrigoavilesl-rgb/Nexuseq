package cl.nexus.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqMathTest {

    private fun state(
        gains: List<Float> = List(Bands.COUNT) { 0f },
        preamp: Float = 0f,
        bass: Float = 0f,
        auto: Boolean = true,
    ) = EqState(gains = gains, preampDb = preamp, bassBoost = bass, autoPreamp = auto)

    @Test
    fun cutoffs_sonMediaGeometrica_yUltimaEs20k() {
        for (i in 0 until Bands.COUNT - 1) {
            val expected = Math.sqrt((Bands.CENTERS[i] * Bands.CENTERS[i + 1]).toDouble()).toFloat()
            assertEquals(expected, Bands.CUTOFFS[i], 0.01f)
        }
        assertEquals(20000f, Bands.CUTOFFS.last(), 0f)
        // estrictamente crecientes: exigencia de DynamicsProcessing
        for (i in 1 until Bands.COUNT) assertTrue(Bands.CUTOFFS[i] > Bands.CUTOFFS[i - 1])
    }

    @Test
    fun flat_noAplicaNada() {
        val s = state()
        assertEquals(0f, EqMath.peakBoost(s), 0f)
        assertEquals(0f, EqMath.effectivePreamp(s), 0f)
        EqMath.effectiveGains(s).forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun autoPreamp_restaElPicoDeBoost() {
        val g = List(Bands.COUNT) { if (it == 2) 6f else 0f }
        assertEquals(-6f, EqMath.effectivePreamp(state(gains = g)), 0f)
        // con auto desactivado manda el preamp manual
        assertEquals(1.5f, EqMath.effectivePreamp(state(gains = g, preamp = 1.5f, auto = false)), 0f)
        // el preamp manual se suma al descuento
        assertEquals(-4f, EqMath.effectivePreamp(state(gains = g, preamp = 2f)), 0f)
    }

    @Test
    fun autoPreamp_ignoraCortes() {
        val g = List(Bands.COUNT) { -5f }
        assertEquals(0f, EqMath.peakBoost(state(gains = g)), 0f)
        assertEquals(0f, EqMath.effectivePreamp(state(gains = g)), 0f)
    }

    @Test
    fun bassBoost_sumaSoloEnGraves_yEstaAcotado() {
        val s = state(bass = 1f)
        val g = EqMath.effectiveGains(s)
        assertEquals(EqMath.BASS_MAX_DB, g[0], 0.001f)
        assertEquals(0f, g[4], 0f)
        assertEquals(0f, g[9], 0f)
        // fader al máximo + bass al máximo no pasa de +20 dB
        val max = state(gains = List(Bands.COUNT) { 12f }, bass = 1f)
        assertEquals(20f, EqMath.effectiveGains(max)[0], 0f)
        // y nunca baja de -12
        val min = state(gains = List(Bands.COUNT) { -12f })
        assertEquals(-12f, EqMath.effectiveGains(min)[0], 0f)
    }

    @Test
    fun curveAt_pasaPorLosCentrosYSeSatura() {
        val g = FloatArray(Bands.COUNT) { it.toFloat() }
        for (i in 0 until Bands.COUNT) assertEquals(g[i], EqMath.curveAt(g, Bands.CENTERS[i]), 0.001f)
        assertEquals(g.first(), EqMath.curveAt(g, 20f), 0f)
        assertEquals(g.last(), EqMath.curveAt(g, 20000f), 0f)
    }

    @Test
    fun curveAt_esMonotonaEntreDosCentros() {
        val g = FloatArray(Bands.COUNT) { 0f }.also { it[5] = 6f }   // 1 kHz = +6, 2 kHz = 0
        var prev = EqMath.curveAt(g, 1000f)
        var f = 1000f
        while (f < 2000f) {
            f *= 1.02f
            val v = EqMath.curveAt(g, f)
            assertTrue("no monótona en $f Hz", v <= prev + 1e-4f)
            prev = v
        }
    }

    @Test
    fun presets_tienenDiezBandasYNombresUnicos() {
        Presets.ALL.forEach { assertEquals(it.name, Bands.COUNT, it.gains.size) }
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.name }.toSet().size)
        Presets.ALL.forEach { p ->
            p.gains.forEach { assertTrue("${p.name} fuera de rango", it in Bands.MIN_DB..Bands.MAX_DB) }
        }
    }
}

class AppNamesTest {
    @Test
    fun conocidas_traducen_ydesconocidas_devuelvenElPaquete() {
        assertEquals("Spotify", AppNames.label("com.spotify.music"))
        assertEquals("Deezer", AppNames.label("deezer.android.app"))
        assertEquals("Apple Music", AppNames.label("com.apple.android.music"))
        assertEquals("com.otra.app", AppNames.label("com.otra.app"))
    }
}
