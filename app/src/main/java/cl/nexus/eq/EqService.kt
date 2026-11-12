package cl.nexus.eq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class EqService : Service() {
    private val engine = EqEngine()
    private lateinit var am: AudioManager
    private val listener: () -> Unit = { sync() }

    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { route() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { route() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        EqStore.init(this)
        running = true
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(deviceCb, Handler(Looper.getMainLooper()))
        EqStore.listeners.add(listener)
        route()
        sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        EqStore.listeners.remove(listener)
        try { am.unregisterAudioDeviceCallback(deviceCb) } catch (_: Throwable) { }
        engine.releaseAll()
        running = false
        super.onDestroy()
    }

    private fun sync() {
        val desired = EqStore.sessions + if (EqStore.global) setOf(0) else emptySet()
        engine.sync(desired)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    /** Elige el perfil según la salida de audio conectada. */
    private fun route() {
        val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        val id = when {
            types.any { it == AudioDeviceInfo.TYPE_USB_DEVICE || it == AudioDeviceInfo.TYPE_USB_HEADSET } -> Out.USB.id
            types.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_BLE_HEADSET } -> Out.BT.id
            types.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> Out.WIRED.id
            else -> Out.SPK.id
        }
        EqStore.setOutput(id)
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "NEXUS.EQ", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = EqStore.sessions.size
        val label = Out.values().firstOrNull { it.id == EqStore.cur }?.label ?: ""
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("NEXUS.EQ activo")
            .setContentText("$label · $n sesión(es) enlazada(s)")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "nexus_eq"
        private const val NOTIF_ID = 1
        @Volatile var running = false

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, EqService::class.java))
            } catch (e: Throwable) {
                Log.w("NexusEQ", "No se pudo iniciar el servicio", e)
            }
        }
    }
}
