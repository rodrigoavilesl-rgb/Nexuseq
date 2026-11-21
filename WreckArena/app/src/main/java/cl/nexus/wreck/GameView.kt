package cl.nexus.wreck

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Superficie de juego: loop propio a ~60 fps y captura multitáctil. */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val world = World()
    private val renderer = Renderer()
    private val input = Input()
    private var loop: LoopThread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        loop = LoopThread(holder).also {
            it.running = true
            it.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        loop?.let {
            it.running = false
            it.join()
        }
        loop = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return true

        val skip = when (e.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> e.actionIndex
            else -> -1
        }
        val cancel = e.actionMasked == MotionEvent.ACTION_CANCEL

        var gas = false
        var brake = false
        var nitro = false
        var steer = 0f
        val r = Layout.btnR(w, h)

        if (!cancel) {
            for (i in 0 until e.pointerCount) {
                if (i == skip) continue
                val x = e.getX(i)
                val y = e.getY(i)
                when {
                    Layout.inside(x, y, Layout.gas(w, h), r) -> gas = true
                    Layout.inside(x, y, Layout.brake(w, h), r) -> brake = true
                    Layout.inside(x, y, Layout.nitro(w, h), r * 0.85f) -> nitro = true
                    x < Layout.steerZoneEnd(w) -> {
                        steer = ((x - Layout.steerCenter(w)) / Layout.steerHalf(w)).coerceIn(-1f, 1f)
                    }
                }
            }
        }

        synchronized(input) {
            input.gas = gas
            input.brake = brake
            input.nitro = nitro
            input.steer = steer
        }
        return true
    }

    private inner class LoopThread(private val holder: SurfaceHolder) : Thread("wreck-loop") {
        @Volatile var running = false

        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val frameStart = System.nanoTime()
                val dt = ((frameStart - last) / 1e9f).coerceAtMost(1f / 30f)
                last = frameStart

                val snap = synchronized(input) { input.copy() }
                world.update(dt, snap)

                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        renderer.draw(canvas, this@GameView.width, this@GameView.height, world, snap)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }

                val spent = (System.nanoTime() - frameStart) / 1_000_000L
                if (spent < 16L) sleep(16L - spent)
            }
        }
    }
}
