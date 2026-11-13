package cl.nexus.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * Cadena DSP sobre UNA sesión de audio:
 *   InputGain (preamp) → PreEQ 10 bandas (incluye bass boost) → Limiter
 *
 * sessionId > 0  → sesión de una app (Spotify la publica al reproducir).
 * sessionId == 0 → mezcla global (deprecado por Android; depende del fabricante).
 *
 * apply() es diferencial: solo envía al efecto lo que cambió desde la última vez
 * (arrastrar un fader = 1-2 llamadas binder en vez de 12). Si una llamada falla,
 * no se marca como aplicada y se reintenta en el siguiente cambio.
 */
class EqEngine private constructor(
    val sessionId: Int,
    private val dp: DynamicsProcessing,
    val layout: BandLayout,
) {
    /** Cantidad de bandas con que se creó el efecto; no se puede cambiar sin recrearlo. */
    val bandCount: Int get() = layout.count

    private val lastGains = arrayOfNulls<Float>(layout.count)
    private var lastPreamp: Float? = null
    private var lastLimiter: Boolean? = null
    private var lastEnabled: Boolean? = null

    fun apply(enabled: Boolean, eq: EqState) {
        val gains = EqMath.effectiveGains(eq)
        if (gains.size != layout.count) return   // el servicio recrea el motor cuando cambia la distribución
        val preamp = EqMath.effectivePreamp(eq)

        if (preamp != lastPreamp) {
            guard("preamp") { dp.setInputGainAllChannelsTo(preamp) }?.let { lastPreamp = preamp }
        }
        for (i in 0 until layout.count) {
            if (gains[i] == lastGains[i]) continue
            guard("banda $i") {
                dp.setPreEqBandAllChannelsTo(
                    i, DynamicsProcessing.EqBand(true, layout.cutoffs[i], gains[i])
                )
            }?.let { lastGains[i] = gains[i] }
        }
        if (eq.limiter != lastLimiter) {
            guard("limitador") {
                dp.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        /* inUse */ true,
                        /* enabled */ eq.limiter,
                        /* linkGroup */ 0,
                        /* attackMs */ 1f,
                        /* releaseMs */ 60f,
                        /* ratio */ 10f,
                        /* thresholdDb */ -1f,
                        /* postGainDb */ 0f,
                    )
                )
            }?.let { lastLimiter = eq.limiter }
        }
        if (enabled != lastEnabled) {
            guard("enable") { dp.enabled = enabled }?.let { lastEnabled = enabled }
        }
    }

    fun release() = runCatching { dp.enabled = false; dp.release() }

    /** Ejecuta [block]; devuelve Unit si salió bien y null si falló (queda registrado). */
    private inline fun guard(what: String, block: () -> Unit): Unit? =
        runCatching(block)
            .onFailure {
                Log.w(TAG, "$what falló en sesión $sessionId", it)
                EqRepository.log("DSP $what falló (sesión $sessionId): ${it.javaClass.simpleName}")
            }
            .getOrNull()

    companion object {
        private const val TAG = "NexusEQ"
        private const val PRIORITY = 1000

        fun create(sessionId: Int, layout: BandLayout = BandLayout.B10): EqEngine? = runCatching {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /* channelCount */ 2,
                /* preEqInUse */ true, layout.count,
                /* mbcInUse */ false, 0,
                /* postEqInUse */ false, 0,
                /* limiterInUse */ true,
            ).setPreferredFrameDuration(10f).build()
            EqEngine(sessionId, DynamicsProcessing(PRIORITY, sessionId, cfg), layout)
        }.onFailure { Log.w(TAG, "No se pudo crear DSP en sesión $sessionId", it) }.getOrNull()
    }
}
