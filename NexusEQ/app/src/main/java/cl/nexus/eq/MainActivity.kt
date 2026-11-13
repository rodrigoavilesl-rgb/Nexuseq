package cl.nexus.eq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import cl.nexus.eq.ui.EqScreen
import cl.nexus.eq.ui.Neon
import cl.nexus.eq.ui.NexusTheme
import cl.nexus.eq.ui.Skins

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI siempre oscura: íconos de barras claros aunque el sistema esté en modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        EqRepository.init(this)
        // La skin se aplica antes del primer dibujo para que no parpadee con el aspecto por defecto.
        EqRepository.state.value.let { Neon.skin = Skins.resolve(it.skinId, it.accent1, it.accent2, it.corners) }
        askNotificationPermission()
        handleIntent(intent)
        setContent { NexusTheme { EqScreen() } }
    }

    override fun onStop() {
        EqRepository.flush()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Spotify › Ecualizador abre esta pantalla con EXTRA_AUDIO_SESSION. */
    private fun handleIntent(intent: Intent?) {
        val fromPanel = intent?.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL &&
            intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR) > 0
        EqService.start(this, if (fromPanel) intent else null)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
