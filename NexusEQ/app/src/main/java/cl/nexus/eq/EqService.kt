package cl.nexus.eq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: escucha las sesiones que abre/cierra Spotify,
 * mantiene un EqEngine por sesión y cambia de perfil según la salida de audio.
 */
class EqService : Service() {

    private val engines = mutableMapOf<Int, EqEngine>()
    private var globalTried = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var am: AudioManager

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = handleSessionIntent(intent)
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshRoute()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshRoute()
    }

    override fun onCreate() {
        super.onCreate()
        EqRepository.init(this)
        am = getSystemService(AUDIO_SERVICE) as AudioManager

        startInForeground()

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        am.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        refreshRoute()

        scope.launch { EqRepository.state.collect { applyAll(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let(::handleSessionIntent)
        return START_STICKY
    }

    /* ───── Sesiones ───── */

    private fun handleSessionIntent(intent: Intent) {
        val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
        if (id <= 0) return
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "desconocida"
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val ok = attach(id)
                EqRepository.setLink(id, AppLink(pkg, ok))
                EqRepository.log("ENLACE ${AppNames.label(pkg)} sesión $id: ${if (ok) "OK" else "ERROR DSP"}")
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                detach(id)
                EqRepository.setLink(id, null)
                EqRepository.log("CIERRE ${AppNames.label(pkg)} sesión $id")
            }
        }
    }

    /** @return true si hay motor activo en la sesión (nuevo o ya existente). */
    private fun attach(id: Int): Boolean {
        if (id in engines) return true
        if (engines.size >= MAX_SESSIONS) return false   // el receiver está exportado: acota sesiones falsas
        val engine = EqEngine.create(id, EqRepository.state.value.current.layout) ?: return false
        engines[id] = engine
        val s = EqRepository.state.value
        engine.apply(s.enabled, s.current)
        publish()
        return true
    }

    private fun detach(id: Int) {
        engines.remove(id)?.release()
        publish()
    }

    private fun applyAll(s: AppState) {
        // Modo global (sesión 0): experimental, depende del fabricante.
        // Si el sistema lo rechaza se intenta UNA vez por activación, no en cada cambio de estado.
        if (s.globalMode) {
            if (0 !in engines && !globalTried) {
                globalTried = true
                EqRepository.setGlobalUnsupported(!attach(0))
            }
        } else {
            globalTried = false
            EqRepository.setGlobalUnsupported(false)
            if (0 in engines) detach(0)
        }
        rebuildIfLayoutChanged(s)
        engines.values.forEach { it.apply(s.enabled, s.current) }
        updateNotification(s)
    }

    /** Al cambiar entre 8/10/16/32 bandas hay que recrear cada efecto: su cantidad de bandas es fija. */
    private fun rebuildIfLayoutChanged(s: AppState) {
        val layout = s.current.layout
        var changed = false
        engines.entries.toList().forEach { (id, engine) ->
            if (engine.bandCount == layout.count) return@forEach
            changed = true
            engine.release()
            val fresh = EqEngine.create(id, layout)
            if (fresh != null) {
                engines[id] = fresh
                EqRepository.log("BANDAS ${layout.count} sesión $id: OK")
            } else {
                engines.remove(id)
                EqRepository.links.value[id]?.let { EqRepository.setLink(id, it.copy(ok = false)) }
                EqRepository.log("BANDAS ${layout.count} sesión $id: ERROR DSP")
            }
        }
        if (changed) publish()
    }

    private fun publish() {
        EqRepository.setSessions(engines.keys.toSet())
        updateNotification(EqRepository.state.value)
    }

    /* ───── Ruta de salida → perfil ───── */

    private fun refreshRoute() {
        val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        val route = when {
            types.any { it in BT_TYPES } -> OutputRoute.BLUETOOTH
            types.any { it in USB_TYPES } -> OutputRoute.USB
            types.any { it in WIRED_TYPES } -> OutputRoute.WIRED
            else -> OutputRoute.SPEAKER
        }
        EqRepository.setRoute(route)
    }

    /* ───── Notificación ───── */

    private fun buildNotification(s: AppState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val status = if (s.enabled) "ON" else "BYPASS"
        val hooked = engines.keys.count { it != 0 }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("NEXUS.EQ · $status · ${s.route.label}")
            .setContentText("Preset ${s.current.presetName} · sesiones: $hooked")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ecualizador activo", NotificationManager.IMPORTANCE_LOW)
        )
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(EqRepository.state.value), type)
    }

    private fun updateNotification(s: AppState) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(sessionReceiver) }
        am.unregisterAudioDeviceCallback(deviceCallback)
        engines.values.forEach { it.release() }
        engines.clear()
        EqRepository.setSessions(emptySet())
        EqRepository.clearLinks()
        EqRepository.flush()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "nexus_eq"
        private const val NOTIF_ID = 42
        private const val MAX_SESSIONS = 8

        private val BT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 /* BLE_HEADSET */, 27 /* BLE_SPEAKER */
        )
        private val USB_TYPES = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
        private val WIRED_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)

        fun start(ctx: Context, extra: Intent? = null) {
            val i = Intent(ctx, EqService::class.java)
            extra?.let {
                i.action = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                    it.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR))
                i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, it.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}

/** Respaldo vía manifest. En Android 8+ el registro dinámico del servicio es el que manda. */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(intent).setClass(ctx, EqService::class.java))
        }
    }
}
