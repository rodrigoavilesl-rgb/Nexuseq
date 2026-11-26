package cl.nexus.eq

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Paquete oficial de Spotify en Android. */
const val SPOTIFY_PACKAGE = "com.spotify.music"

/** Estado de la reproducción de Spotify tal como lo publica su sesión multimedia. */
data class SpotifyNow(
    val linked: Boolean = false,   // hay una sesión de Spotify activa
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Enlace con Spotify por la sesión multimedia del sistema (MediaSession), sin SDK ni Client ID.
 * Lee metadatos y estado, y envía play / pausa / anterior / siguiente.
 * El EQ no depende de este enlace: sigue aplicándose a la sesión de audio de Spotify.
 * Requiere que el usuario conceda "Acceso a notificaciones" a NEXUS.EQ.
 */
object SpotifyLink {
    private val _now = MutableStateFlow(SpotifyNow())
    val now: StateFlow<SpotifyNow> = _now.asStateFlow()

    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = detach()
    }

    /** Elige la sesión de Spotify entre las activas. Se llama al cambiar la lista de sesiones. */
    fun attach(sessions: List<MediaController>) {
        val next = sessions.firstOrNull { it.packageName == SPOTIFY_PACKAGE }
        if (next?.sessionToken == controller?.sessionToken) {
            refresh()
            return
        }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback)
        refresh()
    }

    fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
        _now.value = SpotifyNow()
    }

    fun playPause() {
        val c = controller ?: return
        if (_now.value.playing) c.transportControls?.pause() else c.transportControls?.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            _now.value = SpotifyNow()
            return
        }
        val m = c.metadata
        val s = c.playbackState
        _now.value = SpotifyNow(
            linked = true,
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            playing = s?.state == PlaybackState.STATE_PLAYING,
            positionMs = s?.position ?: 0L,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        )
    }
}

/**
 * Servicio de acceso a notificaciones: es el único permiso que deja a la app ver las sesiones
 * multimedia de otras apps. El usuario lo activa en Ajustes del sistema > Acceso a notificaciones.
 */
class SpotifyListenerService : NotificationListenerService() {

    private var manager: MediaSessionManager? = null

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        SpotifyLink.attach(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        val component = ComponentName(this, SpotifyListenerService::class.java)
        m.addOnActiveSessionsChangedListener(listener, component)
        SpotifyLink.attach(m.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(listener)
        manager = null
        SpotifyLink.detach()
    }
}
