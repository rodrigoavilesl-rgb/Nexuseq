package cl.nexus.eq.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinsTest {
    @Test
    fun skins_tienenIdsUnicos_yGrupos() {
        val ids = Skins.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(13, Skins.ALL.size)
        assertEquals(
            setOf(Skins.G_ORIG, Skins.G_ALIEN, Skins.G_PLAYER, Skins.G_SPORT),
            Skins.GROUPS.keys,
        )
        assertEquals(Skins.ALL.size, Skins.GROUPS.values.sumOf { it.size })
        assertEquals(listOf("XEL", "COLMENA", "ACERO"), Skins.GROUPS.getValue(Skins.G_ALIEN).map { it.id })
    }

    @Test
    fun byId_conocidaYDesconocida() {
        assertEquals(Skins.XEL, Skins.byId("XEL"))
        assertEquals(Skins.METAL, Skins.byId("no-existe"))
    }

    @Test
    fun resolve_sinCambios_devuelveLaSkinBase() {
        Skins.ALL.forEach { assertEquals(it, Skins.resolve(it.id, 0, 0, 0)) }
    }

    @Test
    fun resolve_aplicaAcentosYEsquinas() {
        val r = Skins.resolve("METAL", Color.Red.toArgb(), Color.Blue.toArgb(), 2)
        assertEquals(Color.Red.toArgb(), r.accent1.toArgb())
        assertEquals(Color.Blue.toArgb(), r.accent2.toArgb())
        assertFalse(r.cut)                                  // METAL es de esquinas cortadas; se forzó redondeo
        assertTrue(Skins.resolve("COLMENA", 0, 0, 1).cut)   // COLMENA es redonda; se forzó corte
    }

    @Test
    fun colores_deCadaSkinSonDistintos_paraQueSeLea() {
        Skins.ALL.forEach { s ->
            assertTrue("${s.id}: texto igual al fondo", s.text != s.bg)
            assertTrue("${s.id}: acento igual al fondo", s.accent1 != s.bg && s.accent2 != s.bg)
        }
    }
}
