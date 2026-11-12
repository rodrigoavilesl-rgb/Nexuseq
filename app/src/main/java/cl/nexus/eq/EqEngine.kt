package cl.nexus.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/** Un DynamicsProcessing por sesión de audio: Preamp → EQ 10 bandas → Limitador. */
class EqEngine {
    private val fx = HashMap<Int, DynamicsProcessing>()

    fun sync(desired: Set<Int>) {
        (fx.keys - desired).toList().forEach { detach(it) }
        desired.forEach { attach(it) }
        applyAll()
    }

    private fun attach(session: Int) {
        if (fx.containsKey(session)) return
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, false, 0, false, 0, true, 10, true,
            ).build()
            val dp = DynamicsProcessing(0, session, cfg)
            fx[session] = dp
            apply(dp)
            dp.enabled = true
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo enganchar la sesión $session", e)
        }
    }

    private fun detach(session: Int) {
        try { fx.remove(session)?.release() } catch (e: Throwable) { Log.w(TAG, "release", e) }
    }

    fun applyAll() = fx.values.forEach { apply(it) }

    private fun apply(dp: DynamicsProcessing) {
        try {
            val p = EqStore.profile
            val gains = Eq.eff(p)
            dp.setInputGainAllChannelsTo(Eq.preamp(p))
            for (i in 0 until 10) {
                val cutoff = if (i == 9) 20000f else Eq.CENTER[i] * 1.4142f
                dp.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, gains[i]))
            }
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(true, EqStore.limiter, 0, 1f, 60f, 10f, -1f, 0f)
            )
        } catch (e: Throwable) {
            Log.w(TAG, "apply", e)
        }
    }

    fun releaseAll() = fx.keys.toList().forEach { detach(it) }

    companion object { private const val TAG = "NexusEQ" }
}
