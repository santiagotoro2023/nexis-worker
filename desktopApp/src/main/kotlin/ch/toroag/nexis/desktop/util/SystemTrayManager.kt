package ch.toroag.nexis.desktop.util

import java.awt.AWTException
import java.awt.BasicStroke
import java.awt.Color
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

object SystemTrayManager {

    private var tray:     SystemTray? = null
    private var trayIcon: TrayIcon?   = null

    val isSupported: Boolean get() = SystemTray.isSupported()

    /**
     * Install the tray icon.
     * [onOpen]  — called when the user double-clicks or selects "Open"
     * [onQuit]  — called when the user selects "Quit"
     */
    fun install(onOpen: () -> Unit, onQuit: () -> Unit) {
        if (!SystemTray.isSupported()) return
        try {
            tray = SystemTray.getSystemTray()
            val img = buildTrayImage()

            val popup = PopupMenu()
            MenuItem("Open NeXiS").also { it.addActionListener { onOpen() }; popup.add(it) }
            popup.addSeparator()
            MenuItem("Quit").also       { it.addActionListener { onQuit() }; popup.add(it) }

            trayIcon = TrayIcon(img, "NeXiS", popup).also {
                it.isImageAutoSize = true
                it.addActionListener { onOpen() }  // double-click
                tray!!.add(it)
            }
        } catch (_: AWTException) {}
    }

    fun remove() {
        trayIcon?.let { tray?.remove(it) }
        trayIcon = null
    }

    /**
     * Display a balloon/notification from the tray icon.
     * [level] 0 = info, 1 = warning, 2 = error
     */
    fun notify(title: String, message: String, level: Int = 0) {
        val type = when (level) {
            1    -> TrayIcon.MessageType.WARNING
            2    -> TrayIcon.MessageType.ERROR
            else -> TrayIcon.MessageType.NONE
        }
        trayIcon?.displayMessage(title, message, type)
    }

    /** Triangle-with-eye icon matching the Android launcher icon. */
    private fun buildTrayImage(): BufferedImage {
        val size  = 64
        val img   = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g     = img.createGraphics()
        val orange = Color(0xE8720C)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        // Dark background
        g.color = Color(0x0D0D0A)
        g.fillRect(0, 0, size, size)

        // Scale from 108-unit viewport to `size` px
        val s = size / 108.0
        fun sx(x: Double) = (x * s).toFloat()
        fun sy(y: Double) = (y * s).toFloat()

        // Outer triangle
        val tri = java.awt.Polygon(
            intArrayOf(sx(54.0).toInt(), sx(72.0).toInt(), sx(36.0).toInt()),
            intArrayOf(sy(37.0).toInt(), sy(71.0).toInt(), sy(71.0).toInt()),
            3
        )
        g.color  = orange
        g.stroke = BasicStroke(sx(2.5).coerceAtLeast(1f))
        g.drawPolygon(tri)

        // Center hairline
        g.color  = Color(orange.red, orange.green, orange.blue, (0.35 * 255).toInt())
        g.stroke = BasicStroke(sx(0.8).coerceAtLeast(0.5f))
        g.drawLine(sx(54.0).toInt(), sy(37.0).toInt(), sx(54.0).toInt(), sy(71.0).toInt())

        // Eye ring
        g.color  = orange
        g.stroke = BasicStroke(sx(1.8).coerceAtLeast(1f))
        val er   = sx(6.0); val ex = sx(54.0) - er; val ey = sy(60.0) - er
        g.draw(Ellipse2D.Float(ex, ey, er * 2, er * 2))

        // Iris
        val ir = sx(3.0); val ix = sx(54.0) - ir; val iy = sy(60.0) - ir
        g.fill(Ellipse2D.Float(ix, iy, ir * 2, ir * 2))

        // Pupil highlight
        g.color  = Color(0xFF9533)
        val pr   = sx(1.1); val px = sx(54.0) - pr; val py = sy(60.0) - pr
        g.fill(Ellipse2D.Float(px, py, pr * 2, pr * 2))

        g.dispose()
        return img
    }
}
