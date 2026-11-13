package cl.nexus.eq.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Paleta METAL × CYBERPUNK: acero oscuro, cromo, rojo sangre y neones. */
object Neon {
    val Bg = Color(0xFF07080B)
    val Panel = Color(0xFF0E1118)
    val PanelHi = Color(0xFF181C26)
    val Line = Color(0xFF2A303C)
    val Steel = Color(0xFF8A94A6)
    val Chrome = Color(0xFFD9DEE8)
    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFFF2BD6)
    val Lime = Color(0xFFB6FF3B)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF1F3D)
    val Text = Color(0xFFE6F1FF)
    val Dim = Color(0xFF6B7A99)
    val Mono = FontFamily.Monospace

    /** Esquinas cortadas (look de chapa metálica / HUD). */
    val Cut = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val CutSmall = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)
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
