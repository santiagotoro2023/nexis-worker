package ch.toroag.nexis.desktop.util

import java.awt.AWTException
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.RenderingHints

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

    /** 16×16 orange circle icon drawn in-process — no PNG required. */
    private fun buildTrayImage(): BufferedImage {
        val size = 64
        val img  = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g    = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0xFF6C2A, false)   // NxOrange-ish
        g.fillOval(4, 4, size - 8, size - 8)
        g.color = Color(0xFFFFFF, false)
        g.font  = g.font.deriveFont(32f)
        val fm  = g.fontMetrics
        val txt = "N"
        g.drawString(txt, (size - fm.stringWidth(txt)) / 2, (size + fm.ascent - fm.descent) / 2)
        g.dispose()
        return img
    }
}
