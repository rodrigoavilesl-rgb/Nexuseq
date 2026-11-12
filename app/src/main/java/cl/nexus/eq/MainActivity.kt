package cl.nexus.eq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import cl.nexus.eq.ui.EqScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EqStore.init(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        handle(intent)
        EqService.start(this)
        setContent { EqScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Spotify > Ecualizador abre esta pantalla con su sesión de audio. */
    private fun handle(i: Intent?) {
        if (i?.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) {
            val s = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (s > 0) EqStore.addSession(s)
        }
    }
}
