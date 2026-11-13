package cl.nexus.eq.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Un aspecto completo de la app. Los nombres de color siguen el uso, no el tono:
 * accent1 = acento principal (antes cian), accent2 = secundario (antes magenta),
 * hot = alerta/exceso (antes rojo), ok = activo (antes lima), warn = aviso (antes ámbar).
 */
data class Skin(
    val id: String,
    val name: String,
    val bg: Color,
    val panel: Color,
    val panelHi: Color,
    val line: Color,
    val steel: Color,
    val chrome: Color,
    val text: Color,
    val dim: Color,
    val accent1: Color,
    val accent2: Color,
    val hot: Color,
    val ok: Color,
    val warn: Color,
    val cornerDp: Int,
    val cut: Boolean,
) {
    /** [scale] reduce las esquinas para elementos pequeños. */
    fun shape(scale: Float = 1f): Shape {
        val r = (cornerDp * scale).dp
        return if (cut) CutCornerShape(topStart = r, bottomEnd = r) else RoundedCornerShape(r)
    }
}

object Skins {
    val METAL = Skin(
        id = "METAL", name = "METAL",
        bg = Color(0xFF07080B), panel = Color(0xFF0E1118), panelHi = Color(0xFF181C26),
        line = Color(0xFF2A303C), steel = Color(0xFF8A94A6), chrome = Color(0xFFD9DEE8),
        text = Color(0xFFE6F1FF), dim = Color(0xFF6B7A99),
        accent1 = Color(0xFF00F0FF), accent2 = Color(0xFFFF2BD6), hot = Color(0xFFFF1F3D),
        ok = Color(0xFFB6FF3B), warn = Color(0xFFFFB020), cornerDp = 12, cut = true,
    )

    val NEON = Skin(
        id = "NEON", name = "NEÓN",
        bg = Color(0xFF06020F), panel = Color(0xFF100824), panelHi = Color(0xFF1A0E38),
        line = Color(0xFF3A2A6B), steel = Color(0xFF9A8CCF), chrome = Color(0xFFF2E9FF),
        text = Color(0xFFF5F0FF), dim = Color(0xFF8C7BC4),
        accent1 = Color(0xFFFF2BD6), accent2 = Color(0xFF00F0FF), hot = Color(0xFFFF3B6B),
        ok = Color(0xFF39FF14), warn = Color(0xFFFFE600), cornerDp = 18, cut = true,
    )

    val RETRO = Skin(
        id = "RETRO", name = "RETRO",
        bg = Color(0xFF14100B), panel = Color(0xFF1E1710), panelHi = Color(0xFF2A2016),
        line = Color(0xFF4A3A26), steel = Color(0xFFB59B73), chrome = Color(0xFFF1E6CF),
        text = Color(0xFFF6E8C8), dim = Color(0xFF9C8660),
        accent1 = Color(0xFFFFB347), accent2 = Color(0xFFFF7A2F), hot = Color(0xFFE8452C),
        ok = Color(0xFF7CFC5A), warn = Color(0xFFFFC84A), cornerDp = 6, cut = false,
    )

    val SPORT = Skin(
        id = "SPORT", name = "DEPORTIVA",
        bg = Color(0xFF000000), panel = Color(0xFF0B0B0B), panelHi = Color(0xFF141414),
        line = Color(0xFF262626), steel = Color(0xFF9E9E9E), chrome = Color(0xFFFFFFFF),
        text = Color(0xFFFFFFFF), dim = Color(0xFF7A7A7A),
        accent1 = Color(0xFFC6FF00), accent2 = Color(0xFF00E5FF), hot = Color(0xFFFF3D00),
        ok = Color(0xFF00E676), warn = Color(0xFFFFC400), cornerDp = 10, cut = false,
    )

    val ICE = Skin(
        id = "ICE", name = "HIELO",
        bg = Color(0xFF0A0F14), panel = Color(0xFF111922), panelHi = Color(0xFF18232F),
        line = Color(0xFF2A3A4A), steel = Color(0xFF9FB3C8), chrome = Color(0xFFE8F1F8),
        text = Color(0xFFE8F1F8), dim = Color(0xFF6F8499),
        accent1 = Color(0xFF7DD3FC), accent2 = Color(0xFFA5B4FC), hot = Color(0xFFFB7185),
        ok = Color(0xFF86EFAC), warn = Color(0xFFFCD34D), cornerDp = 14, cut = false,
    )

    val ALL = listOf(METAL, NEON, RETRO, SPORT, ICE)

    fun byId(id: String): Skin = ALL.firstOrNull { it.id == id } ?: METAL

    /** Skin base + los acentos y el estilo de esquinas que eligió el usuario (0 = valor de la skin). */
    fun resolve(id: String, accent1: Int, accent2: Int, corners: Int): Skin {
        var s = byId(id)
        if (accent1 != 0) s = s.copy(accent1 = Color(accent1))
        if (accent2 != 0) s = s.copy(accent2 = Color(accent2))
        s = when (corners) {
            1 -> s.copy(cut = true)
            2 -> s.copy(cut = false)
            else -> s
        }
        return s
    }

    /** Colores que el usuario puede elegir como acento. */
    val SWATCHES: List<Color> = listOf(
        Color(0xFF00F0FF), Color(0xFFFF2BD6), Color(0xFFFF1F3D), Color(0xFFB6FF3B),
        Color(0xFFFFB020), Color(0xFF7C4DFF), Color(0xFFFFFFFF), Color(0xFFFF6A00),
    )
}

/**
 * Acceso global al aspecto activo. Es estado de Compose: cualquier pantalla o dibujo que lea
 * `Neon.Cyan`, etc. se actualiza solo cuando cambia la skin. Los nombres históricos se mantienen.
 */
object Neon {
    var skin: Skin by mutableStateOf(Skins.METAL)

    val Bg: Color get() = skin.bg
    val Panel: Color get() = skin.panel
    val PanelHi: Color get() = skin.panelHi
    val Line: Color get() = skin.line
    val Steel: Color get() = skin.steel
    val Chrome: Color get() = skin.chrome
    val Text: Color get() = skin.text
    val Dim: Color get() = skin.dim
    val Cyan: Color get() = skin.accent1
    val Magenta: Color get() = skin.accent2
    val Red: Color get() = skin.hot
    val Lime: Color get() = skin.ok
    val Amber: Color get() = skin.warn
    val Mono = FontFamily.Monospace

    val Cut: Shape get() = skin.shape(1f)
    val CutSmall: Shape get() = skin.shape(0.6f)
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Neon.Bg,
            surface = Neon.Panel,
            primary = Neon.Cyan,
            secondary = Neon.Magenta,
            tertiary = Neon.Red,
            onBackground = Neon.Text,
            onSurface = Neon.Text,
        ),
        content = content,
    )
}
