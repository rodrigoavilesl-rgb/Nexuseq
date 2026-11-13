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

class PresetsAndReportTest {
    @Test
    fun presets_tienenGrupo_yLosDeConduccionExisten() {
        Presets.ALL.forEach { assertTrue("${it.name} sin grupo", it.group.isNotBlank()) }
        assertEquals(Presets.DRIVE_NAMES.size, Presets.DRIVE.size)
        assertEquals(Presets.ALL.size, Presets.GROUPS.values.sumOf { it.size })
        assertTrue(Presets.GROUPS.containsKey(Presets.G_METAL))
        assertTrue(Presets.GROUPS.containsKey(Presets.G_RETRO))
    }

    @Test
    fun presetsRetro_sonSuaves_noExtremos() {
        Presets.GROUPS.getValue(Presets.G_RETRO).forEach { p ->
            assertTrue("${p.name} demasiado fuerte", p.gains.maxOf { kotlin.math.abs(it) } <= 6f)
        }
    }

    @Test
    fun reporte_incluyeEquipo_apps_yEventos() {
        val app = AppState()
        val links = mapOf(
            1 to AppLink("com.spotify.music", true),
            2 to AppLink("deezer.android.app", false),
        )
        val text = TesterReport.build("1.1", "Samsung SM-A546E · Android 14 (API 34)", app, links, listOf("12:00:00 prueba"))
        assertTrue(text.startsWith("MARAKES TESTER REPORT"))
        assertTrue(text.contains("NEXUS.EQ v1.1"))
        assertTrue(text.contains("Samsung SM-A546E"))
        assertTrue(text.contains("Spotify (com.spotify.music): ENLAZADA"))
        assertTrue(text.contains("Deezer (deezer.android.app): ERROR DSP"))
        assertTrue(text.contains("12:00:00 prueba"))
    }

    @Test
    fun reporte_sinApps_diceNinguna() {
        val text = TesterReport.build("1.1", "x", AppState(), emptyMap(), emptyList())
        assertTrue(text.contains("ninguna"))
        assertTrue(text.contains("sin eventos"))
    }
}

class LayoutsTest {
    @Test
    fun layouts_tienenTamanoCorrecto_yFrecuenciasCrecientes() {
        assertEquals(listOf(8, 10, 16, 32), BandLayout.entries.map { it.count })
        BandLayout.entries.forEach { l ->
            assertEquals(l.count, l.centers.size)
            assertEquals(l.count, l.labels.size)
            assertEquals(l.count, l.cutoffs.size)
            for (i in 1 until l.count) {
                assertTrue("${l.name} centros no crecen en $i", l.centers[i] > l.centers[i - 1])
                assertTrue("${l.name} cortes no crecen en $i", l.cutoffs[i] > l.cutoffs[i - 1])
            }
            for (i in 0 until l.count) assertTrue("${l.name} corte bajo el centro en $i", l.cutoffs[i] > l.centers[i])
            assertTrue(l.cutoffs.last() <= 22000f)
        }
    }

    @Test
    fun forCount_resuelveLayout_yCaeEn10() {
        assertEquals(BandLayout.B32, BandLayout.forCount(32))
        assertEquals(BandLayout.B8, BandLayout.forCount(8))
        assertEquals(BandLayout.B10, BandLayout.forCount(7))
    }

    @Test
    fun bassWeight_conservaLosPesosOriginales_enLos10Centros() {
        val expected = floatArrayOf(1f, 0.75f, 0.4f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f)
        for (i in 0 until 10) assertEquals(expected[i], EqMath.bassWeight(Bands.CENTERS[i]), 0.001f)
        assertEquals(1f, EqMath.bassWeight(16f), 0f)
        assertEquals(0f, EqMath.bassWeight(20000f), 0f)
    }

    @Test
    fun resample_identidad_y_cantidadCorrecta() {
        val g = Presets.ALL.first { it.name == "METAL" }.gains
        assertEquals(g, EqMath.resample(g, BandLayout.B10, BandLayout.B10))
        BandLayout.entries.forEach { l ->
            val r = EqMath.resample(g, BandLayout.B10, l)
            assertEquals(l.count, r.size)
            r.forEach { assertTrue(it in Bands.MIN_DB..Bands.MAX_DB) }
        }
    }

    @Test
    fun resample_planoSigueSiendoPlano_yUnaMesetaSeConserva() {
        val flat = List(10) { 0f }
        assertTrue(EqMath.resample(flat, BandLayout.B10, BandLayout.B32).all { it == 0f })
        val plateau = List(10) { 6f }
        assertTrue(EqMath.resample(plateau, BandLayout.B10, BandLayout.B16).all { it == 6f })
    }

    @Test
    fun withLayout_cambiaLaCantidadDeBandas() {
        val s = EqState().withLayout(BandLayout.B32)
        assertEquals(32, s.gains.size)
        assertEquals(BandLayout.B32, s.layout)
        assertEquals(s, s.withLayout(BandLayout.B32))
    }

    @Test
    fun lowBoost_reflejaElPresetBass_aunConBassBoostEnCero() {
        val p = Presets.ALL.first { it.name == "BASS" }
        assertEquals(6f, EqMath.lowBoost(EqState(gains = p.gains, bassBoost = 0f)), 0.01f)
        assertEquals(14f, EqMath.lowBoost(EqState(gains = p.gains, bassBoost = 1f)), 0.01f)
    }

    @Test
    fun lowBoost_ignoraLosAgudos() {
        val g = List(10) { if (it == 9) 12f else 0f }
        assertEquals(0f, EqMath.lowBoost(EqState(gains = g)), 0f)
    }

    @Test
    fun effectiveGains_funcionaEn32Bandas() {
        val s = EqState(gains = List(32) { 0f }, bassBoost = 1f)
        val e = EqMath.effectiveGains(s)
        assertEquals(32, e.size)
        assertEquals(EqMath.BASS_MAX_DB, e[0], 0.001f)
        assertEquals(0f, e[31], 0f)
    }
}
