package cl.nexus.eq.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object Neon {
    val Bg = Color(0xFF05070D)
    val Panel = Color(0xFF0B101B)
    val Line = Color(0xFF1A2335)
    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFFF2BD6)
    val Lime = Color(0xFFB6FF3B)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF3B5C)
    val Text = Color(0xFFE6F1FF)
    val Dim = Color(0xFF6B7A99)
    val Mono = FontFamily.Monospace
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Neon.Bg,
            surface = Neon.Panel,
            primary = Neon.Cyan,
            secondary = Neon.Magenta,
            onBackground = Neon.Text,
            onSurface = Neon.Text,
        ),
        content = content,
    )
}
