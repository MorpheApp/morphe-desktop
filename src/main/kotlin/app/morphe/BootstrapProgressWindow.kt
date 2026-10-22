/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

import app.morphe.engine.BootstrapProgressListener
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.util.concurrent.CountDownLatch
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private val VOID = Color(0x10, 0x10, 0x10)
private val BORDER = Color(0x2A, 0x2A, 0x2A)
private val TRACK = Color(0x24, 0x24, 0x24)
private val TEXT = Color(0xE3, 0xE3, 0xE3)
private val MUTED = Color(0x80, 0x80, 0x80)
private val NEON = Color(0x3B, 0x7B, 0xF7)
private val ERROR = Color(0xCF, 0x66, 0x79)

private val robotoRegular: Font by lazy { loadBootstrapFont("fonts/Roboto-Regular.ttf") }
private val robotoBold: Font by lazy { loadBootstrapFont("fonts/Roboto-Bold.ttf") }

private fun loadBootstrapFont(resourcePath: String): Font = try {
    BootstrapProgressWindow::class.java.classLoader.getResourceAsStream(resourcePath)!!.use { stream ->
        Font.createFont(Font.TRUETYPE_FONT, stream)
    }
} catch (_: Exception) {
    Font(Font.MONOSPACED, Font.PLAIN, 12)
}

/** Swing setup window displayed while downloading initial GUI dependencies. */
class BootstrapProgressWindow : BootstrapProgressListener {

    private var frame: JFrame? = null
    private var bar: NeonBar? = null
    private var status: JLabel? = null

    override fun onStart(componentCount: Int) {
        SwingUtilities.invokeAndWait {
            val f = JFrame("Morphe")
            f.isUndecorated = true
            f.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            f.isResizable = false
            f.isAlwaysOnTop = true

            val root = JPanel(BorderLayout(0, 18))
            root.background = VOID
            root.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 28),
            )

            val header = JPanel(GridLayout(0, 1, 0, 7))
            header.isOpaque = false
            header.add(label("Morphe", robotoFont(17f, bold = true), TEXT))
            header.add(label("Setting up // Fetching components", robotoFont(10.5f, bold = false), MUTED))

            val progressBar = NeonBar()
            val st = label(" ", robotoFont(10.5f, bold = false), MUTED)

            root.add(header, BorderLayout.NORTH)
            root.add(progressBar, BorderLayout.CENTER)
            root.add(st, BorderLayout.SOUTH)

            f.contentPane = root
            f.pack()
            f.size = Dimension(420, f.height)
            f.setLocationRelativeTo(null)
            f.isVisible = true

            frame = f
            bar = progressBar
            status = st
        }
    }

    override fun onProgress(index: Int, componentCount: Int, displayName: String, bytesDownloaded: Long, totalBytes: Long) {
        val fraction = if (totalBytes > 0) {
            (index + bytesDownloaded.toDouble() / totalBytes) / componentCount
        } else {
            index.toDouble() / componentCount
        }
        SwingUtilities.invokeLater {
            bar?.let {
                it.indeterminate = totalBytes <= 0
                it.progress = fraction
            }
            status?.text = buildString {
                append("Component ${index + 1}/$componentCount")
                if (totalBytes > 0) append("   ${mb(bytesDownloaded)} / ${mb(totalBytes)} MB")
            }
        }
    }

    override fun onComplete() {
        val b = bar ?: return
        SwingUtilities.invokeLater { b.progress = 1.0 }
    }

    override fun onError(message: String) {
        val f = frame ?: return
        // Display error state and block until acknowledged.
        val acknowledged = CountDownLatch(1)
        SwingUtilities.invokeLater {
            f.isAlwaysOnTop = false
            val root = JPanel(BorderLayout(0, 16))
            root.background = VOID
            root.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 28),
            )
            val header = JPanel(GridLayout(0, 1, 0, 7))
            header.isOpaque = false
            header.add(label("Setup failed", robotoFont(15f, bold = true), ERROR))
            header.add(label(message, robotoFont(10.5f, bold = false), MUTED))
            header.add(label("Check your connection and try again", robotoFont(10.5f, bold = false), MUTED))

            val quit = label("Quit", robotoFont(12f, bold = true), ERROR)
            quit.horizontalAlignment = SwingConstants.CENTER
            quit.border = BorderFactory.createEmptyBorder(9, 16, 9, 16)
            quit.cursor = Cursor(Cursor.HAND_CURSOR)
            quit.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = acknowledged.countDown()
            })
            val south = JPanel()
            south.isOpaque = false
            south.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
            south.add(quit)

            root.add(header, BorderLayout.NORTH)
            root.add(south, BorderLayout.SOUTH)
            f.contentPane = root
            f.pack()
            f.size = Dimension(420, f.height)
            f.revalidate()
            f.repaint()
        }
        acknowledged.await()
        SwingUtilities.invokeLater { f.dispose() }
        frame = null
    }

    fun close() {
        val f = frame ?: return
        frame = null
        SwingUtilities.invokeLater { f.dispose() }
    }

    private fun label(text: String, font: Font, color: Color): JLabel =
        JLabel(text).apply {
            this.font = font
            foreground = color
        }

    private fun robotoFont(size: Float, bold: Boolean): Font {
        val base = if (bold) robotoBold else robotoRegular
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.SIZE] = size
        return base.deriveFont(attrs)
    }

    private fun mb(bytes: Long): String = "%.1f".format(bytes / 1_048_576.0)

    private class NeonBar : JComponent() {
        var progress: Double = 0.0
            set(value) {
                field = value.coerceIn(0.0, 1.0)
                repaint()
            }
        var indeterminate: Boolean = false
            set(value) {
                field = value
                repaint()
            }

        override fun getPreferredSize(): Dimension = Dimension(360, 6)

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            g2.color = TRACK
            g2.fillRect(0, 0, w, h)
            g2.color = NEON
            if (indeterminate) {
                g2.color = Color(NEON.red, NEON.green, NEON.blue, 120)
                g2.fillRect(0, 0, w, h)
            } else {
                g2.fillRect(0, 0, (w * progress).toInt(), h)
            }
        }
    }
}
