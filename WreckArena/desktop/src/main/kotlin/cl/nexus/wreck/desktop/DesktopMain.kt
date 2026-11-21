package cl.nexus.wreck.desktop

import cl.nexus.wreck.Input
import cl.nexus.wreck.World
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Controles de teclado:
 *   Flechas / WASD  — dirigir (izq/der) y acelerar (arriba) / frenar-reversa (abajo)
 *   Espacio o Shift — NOS (turbo)
 *   R               — reiniciar la ronda
 */
class GamePanel : JPanel() {
    private val world = World()
    private val input = Input()
    private val renderer = DesktopRenderer()
    private val held = HashSet<Int>()
    private var last = System.nanoTime()

    init {
        preferredSize = Dimension(1280, 720)
        background = Color(5, 2, 12)
        isFocusable = true
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                held += e.keyCode
                if (e.keyCode == KeyEvent.VK_R) world.restart()
            }

            override fun keyReleased(e: KeyEvent) {
                held -= e.keyCode
            }
        })
        Timer(16) { tick() }.start()
    }

    private fun anyHeld(vararg codes: Int) = codes.any { it in held }

    private fun tick() {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceAtMost(1f / 30f)
        last = now

        input.gas = anyHeld(KeyEvent.VK_UP, KeyEvent.VK_W)
        input.brake = anyHeld(KeyEvent.VK_DOWN, KeyEvent.VK_S)
        input.nitro = anyHeld(KeyEvent.VK_SPACE, KeyEvent.VK_SHIFT)
        input.steer = when {
            anyHeld(KeyEvent.VK_LEFT, KeyEvent.VK_A) -> -1f
            anyHeld(KeyEvent.VK_RIGHT, KeyEvent.VK_D) -> 1f
            else -> 0f
        }

        world.update(dt, input)
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        renderer.draw(g, width, height, world, input.copy())
        g.dispose()
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("WRECK ARENA — escritorio")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val panel = GamePanel()
        frame.contentPane.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        panel.requestFocusInWindow()
    }
}
